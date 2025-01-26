package app.vitune.providers.innertube.models

import app.vitune.providers.innertube.Innertube
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @Transient
    val context: Context? = null,
    @Transient
    val cpn: String? = null
) {
    val reason
        get() = if (playabilityStatus != null && playabilityStatus.status != "OK") buildString {
            appendLine("YouTube responded with status '${playabilityStatus.reason.orEmpty()}'")
            playabilityStatus.reason?.let { appendLine("Reason: $it") }
            playabilityStatus.errorScreen?.playerErrorMessageRenderer?.subreason?.text?.let {
                appendLine()
                appendLine(it)
            }
        } else null

    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
        val errorScreen: ErrorScreen? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            internal val loudnessDb: Double?,
            internal val perceptualLoudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = (loudnessDb ?: perceptualLoudnessDb?.plus(7))?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?,
        val expiresInSeconds: Long?
    ) {
        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.filter { it.url != null || it.signatureCipher != null }
                ?.let { formats ->
                    formats.findLast { it.itag == 251 || it.itag == 140 }
                        ?: formats.maxBy { it.bitrate ?: 0L }
                }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            val signatureCipher: String?
        ) {
            suspend fun findUrl(context: Context) =
                url ?: signatureCipher?.let { Innertube.decodeSignatureCipher(context, it) }
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}

@Serializable
data class ErrorScreen(
    val playerErrorMessageRenderer: PlayerErrorMessageRenderer? = null
) {
    @Serializable
    data class PlayerErrorMessageRenderer(
        val subreason: Runs? = null
    )
}
