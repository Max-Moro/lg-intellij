package lg.intellij.cli

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import lg.intellij.models.*
import lg.intellij.services.generation.CliGenerationParams

/**
 * High-level typed API for Listing Generator CLI.
 *
 * Encapsulates argument construction, execution, JSON deserialization,
 * and silent error handling. Equivalent to VS Code's CliClient.ts.
 *
 * All methods are suspend functions that return typed results on success
 * and throw [CliException] subclasses on error.
 *
 * List commands additionally catch [CliNotFoundException] with silent=true
 * and return empty defaults (matching VS Code behavior).
 */
@Service(Service.Level.PROJECT)
class CliClient(private val project: Project) {

    private val log = logger<CliClient>()
    private val executor: CliExecutor get() = project.service()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Separate config for report parsing (needs coerceInputValues for meta field). */
    private val reportJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ========================================
    // List Commands
    // ========================================

    /** List available tokenizer libraries. */
    suspend fun listTokenizerLibs(): List<String> {
        return listCommand(
            "list", "tokenizer-libs",
            timeoutMs = 20_000,
            emptyDefault = emptyList()
        ) { raw ->
            json.decodeFromString<TokenizerLibsListSchema>(raw).tokenizerLibs
        }
    }

    /** List sections for the current project. */
    suspend fun listSections(): List<SectionInfo> {
        return listCommand(
            "list", "sections",
            timeoutMs = 30_000,
            emptyDefault = emptyList()
        ) { raw ->
            json.decodeFromString<SectionsListSchema>(raw).sections
        }
    }

    /** List contexts filtered by provider. */
    suspend fun listContexts(provider: String): List<String> {
        return listCommand(
            "list", "contexts", "--provider", provider,
            timeoutMs = 30_000,
            emptyDefault = emptyList()
        ) { raw ->
            json.decodeFromString<ContextsListSchema>(raw).contexts
        }
    }

    /** List mode-sets for a context and provider. */
    suspend fun listModeSets(context: String, provider: String): ModeSetsListSchema {
        return listCommand(
            "list", "mode-sets", "--context", context, "--provider", provider,
            timeoutMs = 30_000,
            emptyDefault = ModeSetsListSchema(emptyList())
        ) { raw ->
            json.decodeFromString<ModeSetsListSchema>(raw)
        }
    }

    /** List tag-sets for a context. */
    suspend fun listTagSets(context: String): TagSetsListSchema {
        return listCommand(
            "list", "tag-sets", "--context", context,
            timeoutMs = 30_000,
            emptyDefault = TagSetsListSchema(emptyList())
        ) { raw ->
            json.decodeFromString<TagSetsListSchema>(raw)
        }
    }

    /** List encoders for a tokenizer library. */
    suspend fun listEncoders(lib: String): List<String> {
        return listCommand(
            "list", "encoders", "--lib", lib,
            timeoutMs = 20_000,
            emptyDefault = emptyList()
        ) { raw ->
            json.decodeFromString<EncodersListSchema>(raw).encoders
        }
    }

    // ========================================
    // Generation Commands
    // ========================================

    /** Render a target with the given params. Returns raw rendered text. */
    suspend fun render(target: String, params: CliGenerationParams): String {
        val (args, stdinData) = buildGenerationArgs("render", target, params)
        val output = executor.execute(args = args, stdinData = stdinData, timeoutMs = 120_000)
        return output.stdout
    }

    /** Get report for a target. Handles meta-field cleanup internally. */
    suspend fun report(target: String, params: CliGenerationParams): ReportSchema {
        val (args, stdinData) = buildGenerationArgs("report", target, params)
        val output = executor.execute(args = args, stdinData = stdinData, timeoutMs = 120_000)
        return parseReportWithMetaCleanup(output.stdout)
    }

    // ========================================
    // Diagnostic Commands
    // ========================================

    /** Run diagnostics. */
    suspend fun diag(): DiagReportSchema {
        val output = executor.execute(args = listOf("diag"), timeoutMs = 60_000)
        return json.decodeFromString<DiagReportSchema>(output.stdout)
    }

    /** Rebuild cache and run diagnostics. */
    suspend fun diagRebuildCache(): DiagReportSchema {
        val output = executor.execute(args = listOf("diag", "--rebuild-cache"), timeoutMs = 60_000)
        return json.decodeFromString<DiagReportSchema>(output.stdout)
    }

    /** Build diagnostic bundle. Returns (report, bundlePath). */
    suspend fun diagBundle(): Pair<DiagReportSchema, String?> {
        val output = executor.execute(args = listOf("diag", "--bundle"), timeoutMs = 60_000)
        val report = json.decodeFromString<DiagReportSchema>(output.stdout)
        val bundlePath = extractBundlePath(output.stderr)
        return Pair(report, bundlePath)
    }

    // ========================================
    // Internal Helpers
    // ========================================

    /**
     * Execute a list command with silent error handling.
     * Returns [emptyDefault] on silent [CliNotFoundException].
     * Throws on all other errors.
     */
    private suspend fun <T> listCommand(
        vararg args: String,
        timeoutMs: Long,
        emptyDefault: T,
        parse: (String) -> T
    ): T {
        return try {
            val output = executor.execute(args = args.toList(), timeoutMs = timeoutMs)
            parse(output.stdout)
        } catch (e: CliNotFoundException) {
            if (e.silent) {
                log.debug("[${args.joinToString(" ")}] silent failure: ${e.message}")
                emptyDefault
            } else throw e
        }
    }

    /**
     * Build CLI args for render/report commands.
     * Absorbed from CliArgsBuilder.buildCliArgs().
     */
    private fun buildGenerationArgs(
        command: String,
        target: String,
        params: CliGenerationParams
    ): Pair<List<String>, String?> {
        val args = mutableListOf(command, target)

        // Tokenization parameters
        args.add("--lib"); args.add(params.tokenizerLib)
        args.add("--encoder"); args.add(params.encoder)
        args.add("--ctx-limit"); args.add(params.ctxLimit.toString())

        // Modes
        for ((modeSet, mode) in params.modes) {
            if (mode.isNotBlank()) {
                args.add("--mode"); args.add("$modeSet:$mode")
            }
        }

        // Tags (flatten all tags from all tag-sets)
        val allTags = params.tags.values.flatten()
        if (allTags.isNotEmpty()) {
            args.add("--tags"); args.add(allTags.joinToString(","))
        }

        // Target branch (for review mode)
        if (!params.targetBranch.isNullOrBlank()) {
            args.add("--target-branch"); args.add(params.targetBranch.trim())
        }

        // Provider (for template conditions)
        if (!params.providerId.isNullOrBlank()) {
            args.add("--provider"); args.add(params.providerId.trim())
        }

        // Task text (via stdin)
        var stdinData: String? = null
        if (!params.taskText.isNullOrBlank()) {
            args.add("--task"); args.add("-")
            stdinData = params.taskText.trim()
        }

        return Pair(args, stdinData)
    }

    /**
     * Parse report JSON with meta-field cleanup.
     * Replaces polymorphic 'meta' maps with empty objects to avoid
     * kotlinx.serialization issues with heterogeneous value types.
     */
    private fun parseReportWithMetaCleanup(raw: String): ReportSchema {
        val jsonElement = reportJson.parseToJsonElement(raw)
        val jsonObject = jsonElement.jsonObject

        val cleanedFiles = jsonObject["files"]?.jsonArray?.map { fileElement ->
            val fileObj = fileElement.jsonObject.toMutableMap()
            fileObj["meta"] = JsonObject(emptyMap())
            JsonObject(fileObj)
        }?.let { JsonArray(it) }

        val cleanedJson = jsonObject.toMutableMap().apply {
            if (cleanedFiles != null) {
                put("files", cleanedFiles)
            }
        }

        return reportJson.decodeFromJsonElement<ReportSchema>(JsonObject(cleanedJson))
    }

    /** Extract bundle path from stderr. */
    private fun extractBundlePath(stderr: String): String? {
        val regex = Regex("""Diagnostic bundle written to:\s*(.+)\s*$""", RegexOption.MULTILINE)
        return regex.find(stderr)?.groupValues?.getOrNull(1)?.trim()
    }
}
