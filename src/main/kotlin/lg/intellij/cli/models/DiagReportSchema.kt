// To parse the JSON, install kotlin's serialization plugin and do:
//
// val json             = Json { allowStructuredMapKeys = true }
// val diagReportSchema = json.parse(DiagReportSchema.serializer(), jsonString)

package lg.intellij.cli.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable
data class DiagReportSchema (
    val cache: Cache,
    val checks: List<CheckElement>,
    val config: Config,
    val contexts: List<String>,
    val env: Env,
    val protocol: Long,
    val root: String,

    @SerialName("tool_version")
    val toolVersion: String
)

@Serializable
data class Cache (
    val enabled: Boolean,
    val entries: Long,
    val error: String? = null,
    val exists: Boolean,
    val path: String,
    val rebuilt: Boolean,
    val sizeBytes: Long
)

@Serializable
data class CheckElement (
    val details: String? = null,
    val level: Severity,
    val name: String
)

@Serializable
enum class Severity(val value: String) {
    @SerialName("error") Error("error"),
    @SerialName("ok") Ok("ok"),
    @SerialName("warn") Warn("warn");
}

@Serializable
data class Config (
    val actual: Long? = null,
    val applied: List<AppliedElement>? = null,
    val current: Long? = null,
    val error: String? = null,
    val exists: Boolean,
    val fingerprint: String? = null,

    @SerialName("last_error")
    val lastError: LastErrorClass? = null,

    val path: String,
    val sections: List<String>? = null
)

@Serializable
data class AppliedElement (
    val id: Long,
    val title: String
)

@Serializable
data class LastErrorClass (
    val at: String? = null,
    val failed: AppliedElement? = null,
    val message: String,
    val traceback: String? = null
)

@Serializable
data class Env (
    val cwd: String,
    val platform: String,
    val python: String
)
