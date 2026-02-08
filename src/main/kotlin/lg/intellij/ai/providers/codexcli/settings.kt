/**
 * Codex CLI Provider Settings Module.
 *
 * Defines provider-specific settings for Codex CLI:
 * - Reasoning effort level (Minimal/Low/Medium/High/Extra High)
 *
 * Commands:
 * - provider.codex-cli/SELECT_REASONING — update reasoning effort
 */
package lg.intellij.ai.providers.codexcli

import lg.intellij.ai.FieldCommand
import lg.intellij.ai.FieldOption
import lg.intellij.ai.FieldType
import lg.intellij.ai.ProviderSettingsContribution
import lg.intellij.ai.ProviderSettingsField
import lg.intellij.ai.ProviderSettingsModule
import lg.intellij.ai.resolveEnum
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule
import lg.intellij.statepce.withProviderSetting

// ============================================
// Commands
// ============================================

val SelectCodexReasoning = command("provider.codex-cli/SELECT_REASONING").payload<CodexReasoningEffort>()

// ============================================
// Constants
// ============================================

private const val PROVIDER_ID = "com.openai.codex.cli"
private const val SETTINGS_KEY = "codex-cli"

// ============================================
// State Helpers
// ============================================

private data class CodexSettings(
    val reasoning: CodexReasoningEffort
)

/**
 * Extracts Codex settings from providerSettings map.
 */
private fun getCodexSettings(state: PCEState): CodexSettings {
    val settings = state.persistent.providerSettings[SETTINGS_KEY] ?: emptyMap()

    return CodexSettings(
        reasoning = resolveEnum(settings["reasoning"], CodexReasoningEffort.MEDIUM)
    )
}

// ============================================
// Rule Registration
// ============================================

/**
 * Registers Codex CLI settings rules.
 *
 * Called from registerAllDomainRules() in domains/index.kt.
 */
fun registerCodexCliSettingsRules() {
    // When reasoning effort selected, update provider settings
    rule.invoke(SelectCodexReasoning, RuleConfig(
        condition = { _: PCEState, _: CodexReasoningEffort -> true },
        apply = { _: PCEState, effort: CodexReasoningEffort ->
            lgResult(persistent = { s ->
                s.withProviderSetting(SETTINGS_KEY, "reasoning", effort)
            })
        }
    ))
}

// ============================================
// Provider Settings Module
// ============================================

/**
 * Settings module for Codex CLI provider.
 *
 * Provides one UI field:
 * - Reasoning effort selector (Minimal/Low/Medium/High/Extra High)
 */
val codexCliSettings = object : ProviderSettingsModule {
    override val providerId = PROVIDER_ID

    override val stateDefaults = mapOf<String, Any?>(
        "reasoning" to CodexReasoningEffort.MEDIUM
    )

    override fun buildContribution(state: PCEState): ProviderSettingsContribution {
        val isVisible = state.persistent.providerId == PROVIDER_ID
        val settings = getCodexSettings(state)

        return ProviderSettingsContribution(
            providerId = providerId,
            title = "Codex Settings",
            visible = isVisible,
            fields = listOf(
                ProviderSettingsField(
                    id = "codexReasoningEffort",
                    type = FieldType.SELECT,
                    label = "Reasoning",
                    options = CodexReasoningEffort.getAvailableEfforts().map { desc ->
                        FieldOption(
                            value = desc.id.name,
                            label = desc.label,
                            description = desc.description
                        )
                    },
                    value = settings.reasoning.name,
                    command = FieldCommand(
                        type = SelectCodexReasoning.type,
                        payloadKey = "effort"
                    )
                )
            )
        )
    }
}
