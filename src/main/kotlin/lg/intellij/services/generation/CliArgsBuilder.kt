package lg.intellij.services.generation

import lg.intellij.models.SectionInfo
import lg.intellij.statepce.PCEStateStore

/**
 * Parameters for CLI generation commands.
 *
 * Equivalent to VS Code's CliGenerationParams interface.
 */
data class CliGenerationParams(
    val tokenizerLib: String,
    val encoder: String,
    val ctxLimit: Int,
    val modes: Map<String, String>,
    val tags: Map<String, Set<String>>,
    val taskText: String?,
    val targetBranch: String?,
    val providerId: String?
)

/**
 * Options for building CLI params from store.
 *
 * Equivalent to VS Code's BuildCliParamsOptions interface.
 *
 * @param includeProvider Whether to include providerId in CLI args (contexts yes, sections no)
 * @param sectionInfo When provided, filters modes/tags to only compatible ones for that section
 */
data class BuildParamsOptions(
    val includeProvider: Boolean = false,
    val sectionInfo: SectionInfo? = null
)

/**
 * Helper utility for building CLI command arguments.
 *
 * Centralizes logic for constructing CLI command arguments
 * and extracting parameters from PCE state.
 *
 * Naming aligned with VS Code's ParamsBuilder.ts + CliClient.ts:
 * - buildCliParams() ↔ buildCliParams()
 * - buildCliArgs()   ↔ buildCliArgs()
 */
object CliArgsBuilder {

    /**
     * Builds CLI arguments for render/report commands.
     *
     * Equivalent to VS Code's buildCliArgs(command, target, params).
     *
     * @param command CLI command ("render" or "report")
     * @param target Target specifier (e.g., "sec:all", "ctx:template-name")
     * @param params CLI generation parameters
     * @return Pair of (args list, stdin data or null)
     */
    fun buildCliArgs(
        command: String,
        target: String,
        params: CliGenerationParams
    ): Pair<List<String>, String?> {
        val args = mutableListOf(command, target)

        // Tokenization parameters
        args.add("--lib")
        args.add(params.tokenizerLib)

        args.add("--encoder")
        args.add(params.encoder)

        args.add("--ctx-limit")
        args.add(params.ctxLimit.toString())

        // Modes
        for ((modeSet, mode) in params.modes) {
            if (mode.isNotBlank()) {
                args.add("--mode")
                args.add("$modeSet:$mode")
            }
        }

        // Tags (flatten all tags from all tag-sets)
        val allTags = params.tags.values.flatten()
        if (allTags.isNotEmpty()) {
            args.add("--tags")
            args.add(allTags.joinToString(","))
        }

        // Target branch (for review mode)
        if (!params.targetBranch.isNullOrBlank()) {
            args.add("--target-branch")
            args.add(params.targetBranch.trim())
        }

        // Provider (for template conditions)
        if (!params.providerId.isNullOrBlank()) {
            args.add("--provider")
            args.add(params.providerId.trim())
        }

        // Task text (via stdin)
        var stdinData: String? = null
        if (!params.taskText.isNullOrBlank()) {
            args.add("--task")
            args.add("-") // Read from stdin
            stdinData = params.taskText.trim()
        }

        return Pair(args, stdinData)
    }

    /**
     * Extracts generation parameters from PCEStateStore.
     *
     * Equivalent to VS Code's buildCliParams(state, options).
     *
     * @param store PCE state store
     * @param options Options controlling provider inclusion and section filtering
     * @return CLI generation parameters
     */
    fun buildCliParams(
        store: PCEStateStore,
        options: BuildParamsOptions = BuildParamsOptions()
    ): CliGenerationParams {
        val state = store.getBusinessState()
        val ctx = state.persistent.template
        val provider = state.persistent.providerId

        var modes = store.getCurrentModes(ctx, provider)
        var tags = store.getCurrentTags(ctx)

        // Filter modes and tags if section info provided
        if (options.sectionInfo != null) {
            modes = filterModesForSection(modes, options.sectionInfo)
            tags = filterTagsForSection(tags, options.sectionInfo)
        }

        // targetBranch is only relevant in review mode
        val isReviewMode = modes.values.any { it == "review" }

        return CliGenerationParams(
            tokenizerLib = state.persistent.tokenizerLib,
            encoder = state.persistent.encoder,
            ctxLimit = state.persistent.ctxLimit,
            modes = modes,
            tags = tags,
            taskText = state.persistent.taskText.takeIf { it.isNotBlank() },
            targetBranch = if (isReviewMode) state.persistent.targetBranch.takeIf { it.isNotBlank() } else null,
            providerId = if (options.includeProvider) provider.takeIf { it.isNotBlank() } else null
        )
    }

    /**
     * Filter modes to only include those compatible with the section.
     */
    private fun filterModesForSection(
        modes: Map<String, String>,
        sectionInfo: SectionInfo
    ): Map<String, String> {
        val compatibleModeSetIds = sectionInfo.modeSets.map { it.id }.toSet()
        return modes.filterKeys { it in compatibleModeSetIds }
    }

    /**
     * Filter tags to only include those compatible with the section.
     */
    private fun filterTagsForSection(
        tags: Map<String, Set<String>>,
        sectionInfo: SectionInfo
    ): Map<String, Set<String>> {
        val compatibleTagSetIds = sectionInfo.tagSets.map { it.id }.toSet()
        return tags.filterKeys { it in compatibleTagSetIds }
    }
}
