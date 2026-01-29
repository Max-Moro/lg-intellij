// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json         = Json { allowStructuredMapKeys = true }
// val reportSchema = json.parse(ReportSchema.serializer(), jsonString)

package lg.intellij.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable
data class ReportSchema (
    val context: Context? = null,
    val ctxLimit: Long,
    val encoder: String,
    val files: List<File>,
    val protocol: Long,
    val scope: Scope,
    val target: String,

    @SerialName("tokenizerLib")
    val tokenizerLIB: String,

    val total: Total
)

@Serializable
data class Context (
    val finalCtxShare: Double? = null,
    val finalRenderedTokens: Long? = null,
    val sectionsUsed: Map<String, Long>,
    val templateName: String,
    val templateOnlyTokens: Long? = null,
    val templateOverheadPct: Double? = null
)

@Serializable
data class File (
    val ctxShare: Double,
    val meta: Map<String, Meta>,
    val path: String,
    val promptShare: Double,
    val savedPct: Double,
    val savedTokens: Long,
    val sizeBytes: Long,
    val tokensProcessed: Long,
    val tokensRaw: Long
)

@Serializable
sealed class Meta {
    class BoolValue(val value: Boolean)  : Meta()
    class DoubleValue(val value: Double) : Meta()
    class IntegerValue(val value: Long)  : Meta()
    class StringValue(val value: String) : Meta()
}

@Serializable
enum class Scope(val value: String) {
    @SerialName("context") Context("context"),
    @SerialName("section") Section("section");
}

@Serializable
data class Total (
    val ctxShare: Double,
    val metaSummary: Map<String, Long>,
    val renderedOverheadTokens: Long? = null,
    val renderedTokens: Long? = null,
    val savedPct: Double,
    val savedTokens: Long,
    val sizeBytes: Long,
    val tokensProcessed: Long,
    val tokensRaw: Long
)
