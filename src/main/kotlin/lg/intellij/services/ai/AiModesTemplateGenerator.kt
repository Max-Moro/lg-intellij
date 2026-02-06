package lg.intellij.services.ai

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Generator for ai-interaction.sec.yaml template file.
 *
 * Collects supported modes from all registered AI providers
 * and generates/updates the canonical integration meta-section.
 */
@Service(Service.Level.PROJECT)
class AiModesTemplateGenerator(private val project: Project) {

    private val log = logger<AiModesTemplateGenerator>()

    /**
     * Canonical mode definitions with metadata.
     */
    private data class ModeMetadata(
        val title: String,
        val description: String? = null,
        val tags: List<String>? = null
    )

    private val canonicalModes = mapOf(
        "ask" to ModeMetadata(
            title = "Ask",
            description = "Question-answer mode"
        ),
        "agent" to ModeMetadata(
            title = "Agent",
            description = "Agent mode with tools",
            tags = listOf("agent")
        ),
        "plan" to ModeMetadata(
            title = "Plan",
            description = "Planning / specification mode",
            tags = listOf("agent", "plan")
        )
    )

    /**
     * Generates or updates the ai-interaction.sec.yaml file.
     *
     * @return Path to generated file
     * @throws IllegalStateException if project base path not found
     */
    fun generate(): String {
        val basePath = project.basePath
            ?: throw IllegalStateException("Project base path not found")

        val lgCfgDir = File(basePath, "lg-cfg")
        val filePath = File(lgCfgDir, "ai-interaction.sec.yaml")

        log.info("Generating ${filePath.absolutePath}")

        // Ensure lg-cfg directory exists
        if (!lgCfgDir.exists()) {
            lgCfgDir.mkdirs()
            log.debug("Created lg-cfg directory")
        }

        // Collect modes from all providers
        val aiService = AiIntegrationService.getInstance()
        val allModes = aiService.getAllSupportedModes()
        log.debug("Collected modes for ${allModes.size} mode types")

        // Read existing file if present
        val existingContent = if (filePath.exists()) {
            filePath.readText()
        } else {
            ""
        }

        // Merge and generate new content
        val newContent = mergeAndGenerate(existingContent, allModes)

        // Write file
        filePath.writeText(newContent)
        log.info("Written ${filePath.absolutePath}")

        return filePath.absolutePath
    }

    /**
     * Merges existing content with new modes data.
     */
    private fun mergeAndGenerate(
        existingContent: String,
        allModes: Map<String, Map<String, String>>
    ): String {
        // If no existing content, generate fresh
        if (existingContent.isBlank()) {
            return generateFresh(allModes)
        }

        // Parse existing runs per mode
        val existingRuns = parseExistingRuns(existingContent).toMutableMap()

        // Merge: new providers override, unknown providers preserved
        for ((modeId, providers) in allModes) {
            val modeRuns = existingRuns.getOrPut(modeId) { mutableMapOf() }.toMutableMap()
            for ((providerId, runs) in providers) {
                modeRuns[providerId] = runs
            }
            existingRuns[modeId] = modeRuns
        }

        // Regenerate with merged data
        return generateFresh(existingRuns)
    }

    /**
     * Parses existing YAML to extract runs per mode.
     */
    private fun parseExistingRuns(content: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()

        // Simple line-based parsing
        var currentMode: String? = null
        var inRuns = false

        for (line in content.lines()) {
            // Detect mode start (8 spaces indent)
            val modeMatch = Regex("""^ {8}(\w+):\s*$""").find(line)
            if (modeMatch != null) {
                currentMode = modeMatch.groupValues[1]
                inRuns = false
                continue
            }

            // Detect runs section
            if (line.trim() == "runs:" && currentMode != null) {
                inRuns = true
                result.getOrPut(currentMode) { mutableMapOf() }
                continue
            }

            // Parse provider runs (12 spaces indent)
            if (inRuns && currentMode != null) {
                val runsMatch = Regex("""^ {12}([a-z0-9._-]+):\s*"?([^"]*)"?\s*$""").find(line)
                if (runsMatch != null) {
                    val providerId = runsMatch.groupValues[1]
                    val runsValue = runsMatch.groupValues[2].trim()
                    result[currentMode]?.put(providerId, runsValue)
                } else if (!line.startsWith(" ".repeat(12)) && line.isNotBlank()) {
                    // End of runs section
                    inRuns = false
                }
            }
        }

        return result
    }

    /**
     * Generates fresh YAML content from modes data.
     */
    private fun generateFresh(allModes: Map<String, Map<String, String>>): String {
        val lines = mutableListOf(
            "# Auto-generated by Listing Generator IDE plugins",
            "# This file defines AI provider integration modes.",
            "# Manual edits to unknown providers/modes will be preserved.",
            "",
            "ai-interaction:",
            "  mode-sets:",
            "    ai-interaction:",
            "      title: \"AI Interaction\"",
            "      modes:"
        )

        // Process canonical modes first (in order)
        val processedModes = mutableSetOf<String>()
        for ((modeId, meta) in canonicalModes) {
            val providers = allModes[modeId]
            if (!providers.isNullOrEmpty()) {
                appendMode(lines, modeId, meta, providers)
                processedModes.add(modeId)
            }
        }

        // Process any non-canonical modes (from existing file)
        for ((modeId, providers) in allModes) {
            if (modeId !in processedModes && providers.isNotEmpty()) {
                appendMode(lines, modeId, ModeMetadata(title = modeId), providers)
            }
        }

        return lines.joinToString("\n") + "\n"
    }

    /**
     * Appends a mode block to lines list.
     */
    private fun appendMode(
        lines: MutableList<String>,
        modeId: String,
        meta: ModeMetadata,
        providers: Map<String, String>
    ) {
        lines.add("        $modeId:")
        lines.add("          title: \"${meta.title}\"")

        meta.description?.let {
            lines.add("          description: \"$it\"")
        }

        meta.tags?.let {
            if (it.isNotEmpty()) {
                lines.add("          tags: [${it.joinToString(", ")}]")
            }
        }

        lines.add("          runs:")

        // Sort providers for consistent output
        for ((providerId, runs) in providers.toSortedMap()) {
            lines.add("            $providerId: \"$runs\"")
        }
    }

    companion object {
        fun getInstance(project: Project): AiModesTemplateGenerator = project.service()
    }
}
