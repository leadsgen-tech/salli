package lk.salli.data.ai

/**
 * The single AI-mode model we ship against. Kept as a named constant rather than user-facing
 * config — choosing between quantizations and sizes is exactly the kind of decision we shouldn't
 * ask the user to make.
 *
 * This is Qwen 2.5 0.5B Instruct quantized to int8 (multi-prefill, ekv1280). 547 MB, runs via
 * MediaPipe's `LlmInference` on the LiteRT stack. Why not the obvious choice of Gemma 3 1B?
 * Because Gemma is gated on both HuggingFace and Kaggle — every download route requires a
 * license click-through plus a per-user access token. Qwen is ungated: `curl -L` against the
 * resolve URL returns 200 anonymously, which means `DownloadManager.enqueue` inside the app
 * works without any auth plumbing. The capability gap (0.5B vs 1B params) is real but
 * manageable for narrow-domain extraction — SMS bodies are short, the schema is fixed, and we
 * can fine-tune later on the redacted corpus for the tasks where 0.5B zero-shot slips.
 */
object LocalModel {
    const val FILENAME: String = "qwen2.5-0.5b-it.task"

    const val DOWNLOAD_URL: String =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"

    /** Expected disk size in bytes — used for progress calculation and "free space?" check. */
    const val SIZE_BYTES: Long = 547L * 1024L * 1024L

    /** Display label for the user. Keep short — appears under the AI radio row. */
    const val DISPLAY_NAME: String = "Qwen 2.5 0.5B"
}
