package lg.intellij.services.generation

import lg.intellij.statepce.PCEStateStore

/**
 * Helper utility for building CLI command arguments.
 *
 * Centralizes logic for constructing 'lg render' and 'lg report' command arguments
 * to avoid duplication across services and ensure consistency.
 */
object CliArgsBuilder {

    /**
     * Parameters for CLI generation commands.
     */
    data class GenerationParams(
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
     * Builds arguments for 'lg render' command.
     *
     * @param target Target specifier (e.g., "sec:all", "ctx:template-name")
     * @param params Generation parameters
     * @return Pair of (args list, stdin data or null)
     */
    fun buildRenderArgs(
        target: String,
        params: GenerationParams
    ): Pair<List<String>, String?> {
        val args = mutableListOf("render", target)

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
     * Builds arguments for 'lg report' command.
     *
     * @param target Target specifier (e.g., "sec:all", "ctx:template-name")
     * @param params Generation parameters
     * @return Pair of (args list, stdin data or null)
     */
    fun buildReportArgs(
        target: String,
        params: GenerationParams
    ): Pair<List<String>, String?> {
        // Same as render, but command is 'report'
        val (renderArgs, stdinData) = buildRenderArgs(target, params)
        val reportArgs = renderArgs.toMutableList()
        reportArgs[0] = "report" // Replace 'render' with 'report'

        return Pair(reportArgs, stdinData)
    }

    /**
     * Extracts generation parameters from PCEStateStore.
     *
     * @param store PCE state store
     * @return Generation parameters
     */
    fun fromStore(store: PCEStateStore): GenerationParams {
        val state = store.getBusinessState()
        val ctx = state.persistent.template
        val provider = state.persistent.providerId

        return GenerationParams(
            tokenizerLib = state.persistent.tokenizerLib,
            encoder = state.persistent.encoder,
            ctxLimit = state.persistent.ctxLimit,
            modes = store.getCurrentModes(ctx, provider),
            tags = store.getCurrentTags(ctx),
            taskText = state.persistent.taskText.takeIf { it.isNotBlank() },
            targetBranch = state.persistent.targetBranch.takeIf { it.isNotBlank() },
            providerId = provider.takeIf { it.isNotBlank() }
        )
    }
}