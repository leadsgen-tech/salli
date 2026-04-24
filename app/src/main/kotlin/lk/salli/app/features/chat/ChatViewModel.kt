package lk.salli.app.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lk.salli.app.ai.LocalLlmRunner
import lk.salli.data.ai.ModelManager
import lk.salli.data.prefs.SalliPreferences
import lk.salli.domain.ParseMode

/**
 * One message in the chat. `id` is a monotonically increasing counter so Compose can use it as
 * a stable key for list animation without depending on content equality.
 */
data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
) {
    enum class Role { USER, ASSISTANT }
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val thinking: Boolean = false,
    /** True when both the model file is present AND the user has AI mode enabled. */
    val ready: Boolean = false,
    /** Only populated when ready is false, explaining why. */
    val blockReason: String? = null,
    val lastLatencyMs: Long? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmRunner: LocalLlmRunner,
    private val modelManager: ModelManager,
    private val prefs: SalliPreferences,
    private val contextBuilder: ChatContextBuilder,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var financialContext: String = ""
    private var nextId: Long = 1

    init {
        viewModelScope.launch {
            // Build the context once on open. For v1 we don't refresh it per-turn — a single
            // chat session is assumed to be short (seconds to minutes), so stale-by-a-minute
            // data is fine. A smarter pass could re-build after every Nth turn.
            financialContext = runCatching { contextBuilder.build() }.getOrDefault("")
            refreshReadiness()
        }
    }

    private suspend fun refreshReadiness() {
        // Gate only on the model file being present — chat is orthogonal to the parse-mode
        // preference, which governs SMS ingest rather than AI-assisted Q&A.
        val installed = modelManager.isInstalled()
        val (ready, reason) = if (installed) {
            true to null
        } else {
            false to "The AI model isn't downloaded yet. Grab it from Settings to chat."
        }
        _state.value = _state.value.copy(ready = ready, blockReason = reason)
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _state.value.thinking || !_state.value.ready) return

        val userMsg = ChatMessage(id = nextId++, role = ChatMessage.Role.USER, text = trimmed)
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            thinking = true,
        )

        viewModelScope.launch {
            val prompt = buildPrompt(trimmed, _state.value.messages.dropLast(1))
            // Let the runner use its default engine budget (1280 total tokens). Our context
            // snapshot is ~400 tokens so there's plenty of room for question + reply.
            val outcome = runCatching { llmRunner.run(prompt, temperature = 0.2f) }
            outcome.fold(
                onSuccess = { completion ->
                    val cleaned = stripArtefacts(completion.text)
                    val reply = ChatMessage(
                        id = nextId++,
                        role = ChatMessage.Role.ASSISTANT,
                        text = cleaned.ifBlank { "Hmm, I didn't get anything back. Try rephrasing?" },
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + reply,
                        thinking = false,
                        lastLatencyMs = completion.inferMillis,
                    )
                },
                onFailure = { t ->
                    val reply = ChatMessage(
                        id = nextId++,
                        role = ChatMessage.Role.ASSISTANT,
                        text = "Something went wrong: ${t.message ?: t.javaClass.simpleName}",
                    )
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + reply,
                        thinking = false,
                    )
                },
            )
        }
    }

    fun suggest(prompt: String) = send(prompt)

    fun clear() {
        _state.value = _state.value.copy(messages = emptyList(), lastLatencyMs = null)
    }

    private fun buildPrompt(userMessage: String, priorHistory: List<ChatMessage>): String {
        val system = """
You are Salli, a helpful financial assistant answering questions about the user's on-device money data.
Respond in one or two short sentences. Use the Sri Lankan rupee (Rs) format. Never invent numbers
that aren't in the snapshot below — if you don't see the answer, say so briefly.

${financialContext.trim()}
""".trim()

        // Keep only the last 6 turns to stay within the context budget.
        val history = priorHistory.takeLast(6).joinToString("\n") { m ->
            val speaker = if (m.role == ChatMessage.Role.USER) "User" else "Salli"
            "$speaker: ${m.text}"
        }

        return buildString {
            append(system)
            append("\n\n")
            if (history.isNotBlank()) {
                append(history)
                append("\n")
            }
            append("User: ")
            append(userMessage)
            append("\nSalli:")
        }
    }

    /**
     * Strip special chat tokens some models leak and trim trailing nonsense. Qwen in particular
     * likes to emit `<|im_end|>` or continue with a fresh `User:` line.
     */
    private fun stripArtefacts(raw: String): String {
        var out = raw.trim()
        listOf("<|im_end|>", "<|endoftext|>", "</s>").forEach { out = out.replace(it, "") }
        // Cut at the first post-reply continuation if the model kept talking.
        val cut = listOf("\nUser:", "\nSalli:", "\nUSER:", "User:").minOfOrNull {
            val idx = out.indexOf(it)
            if (idx >= 0) idx else Int.MAX_VALUE
        } ?: Int.MAX_VALUE
        if (cut != Int.MAX_VALUE) out = out.substring(0, cut)
        return out.trim()
    }
}
