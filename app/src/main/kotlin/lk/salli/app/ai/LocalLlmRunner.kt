package lk.salli.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lk.salli.data.ai.ModelManager

/**
 * Loads the on-device LLM once and serves prompts serially. The underlying MediaPipe
 * `LlmInference` is expensive to construct (~3–6 s on a Pixel 6) but cheap to invoke, so we
 * hold a single instance for the process lifetime.
 *
 * Concurrency: MediaPipe's session object is not safe for parallel `generateResponseAsync`
 * calls. A mutex serialises callers — fine for the SMS pipeline where events arrive one at a
 * time.
 */
@Singleton
class LocalLlmRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
) {

    private val mutex = Mutex()
    @Volatile private var engine: LlmInference? = null

    data class Completion(val text: String, val loadMillis: Long, val inferMillis: Long)

    suspend fun run(
        prompt: String,
        maxTokens: Int = 1280,
        temperature: Float = 0.1f,
        topK: Int = 40,
    ): Completion = mutex.withLock {
        val file = modelManager.modelFile
        require(file.exists()) { "Model file missing at ${file.absolutePath}" }

        var loadMs = 0L
        val llm = engine ?: withContext(Dispatchers.IO) {
            loadMs = measureTimeMillis {
                // NB: setMaxTokens here is the TOTAL budget (prompt + generated). Our model
                // was exported with ekv1280, so 1280 is the ceiling. Using the full ceiling
                // lets the chat context (~500 tokens) + question + reply fit comfortably.
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(file.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build()
                engine = LlmInference.createFromOptions(context, options)
            }
            engine!!
        }

        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(temperature)
            .setTopK(topK)
            .build()

        val text: String
        val inferMs = measureTimeMillis {
            LlmInferenceSession.createFromOptions(llm, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                text = awaitGenerate(session)
            }
        }

        Completion(text = text, loadMillis = loadMs, inferMillis = inferMs)
    }

    /**
     * Bridge MediaPipe's `ListenableFuture<String>` onto a cancellable coroutine. We intentionally
     * use the non-streaming variant; streaming is nicer for chat UIs but our SMS parser only
     * cares about the final payload.
     */
    private suspend fun awaitGenerate(session: LlmInferenceSession): String =
        suspendCancellableCoroutine { cont ->
            val future = session.generateResponseAsync { partial, done ->
                // progress callback — ignored; we wait for the final result.
                @Suppress("UNUSED_ANONYMOUS_PARAMETER") partial
                @Suppress("UNUSED_ANONYMOUS_PARAMETER") done
            }
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { cont.resumeWith(Result.success(it)) }
                        .onFailure { cont.resumeWith(Result.failure(it)) }
                },
                Runnable::run,
            )
            cont.invokeOnCancellation { future.cancel(true) }
        }

    /**
     * Drop the engine (and its ~1 GB working set). Call when the user switches back to Standard
     * mode or deletes the model file.
     */
    fun release() {
        engine?.close()
        engine = null
    }
}
