/**
 * Claude CLI Provider Settings Module.
 *
 * Defines provider-specific settings for Claude CLI:
 * - Model selection (Haiku/Sonnet/Opus)
 * - Integration method (Memory File/Session)
 *
 * Commands:
 * - provider.claude-cli/SELECT_MODEL — update selected model
 * - provider.claude-cli/SELECT_METHOD — update integration method
 */
package lg.intellij.ai.providers.claudecli

import lg.intellij.ai.FieldCommand
import lg.intellij.ai.FieldOption
import lg.intellij.ai.FieldType
import lg.intellij.ai.ProviderSettingsContribution
import lg.intellij.ai.ProviderSettingsField
import lg.intellij.ai.ProviderSettingsModule
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule
import lg.intellij.statepce.withProviderSetting

// ============================================
// Commands
// ============================================

val SelectClaudeModel = command("provider.claude-cli/SELECT_MODEL").payload<ClaudeModel>()
val SelectClaudeMethod = command("provider.claude-cli/SELECT_METHOD").payload<ClaudeIntegrationMethod>()

// ============================================
// Constants
// ============================================

private const val PROVIDER_ID = "com.anthropic.claude.cli"
private const val SETTINGS_KEY = "claude-cli"

// ============================================
// State Helpers
// ============================================

private data class ClaudeSettings(
    val model: ClaudeModel,
    val method: ClaudeIntegrationMethod
)

/**
 * Extracts Claude settings from providerSettings map.
 */
private fun getClaudeSettings(state: PCEState): ClaudeSettings {
    val settings = state.persistent.providerSettings[SETTINGS_KEY] ?: emptyMap()

    return ClaudeSettings(
        model = (settings["model"] as? ClaudeModel) ?: ClaudeModel.SONNET,
        method = (settings["method"] as? ClaudeIntegrationMethod) ?: ClaudeIntegrationMethod.SESSION
    )
}

// ============================================
// Rule Registration
// ============================================

/**
 * Registers Claude CLI settings rules.
 *
 * Called from registerAllDomainRules() in domains/index.kt.
 */
fun registerClaudeCliSettingsRules() {
    // When Claude model selected, update provider settings
    rule.invoke(SelectClaudeModel, RuleConfig(
        condition = { _: PCEState, _: ClaudeModel -> true },
        apply = { _: PCEState, model: ClaudeModel ->
            lgResult(persistent = { s ->
                s.withProviderSetting(SETTINGS_KEY, "model", model)
            })
        }
    ))

    // When Claude method selected, update provider settings
    rule.invoke(SelectClaudeMethod, RuleConfig(
        condition = { _: PCEState, _: ClaudeIntegrationMethod -> true },
        apply = { _: PCEState, method: ClaudeIntegrationMethod ->
            lgResult(persistent = { s ->
                s.withProviderSetting(SETTINGS_KEY, "method", method)
            })
        }
    ))
}

// ============================================
// Provider Settings Module
// ============================================

/**
 * Settings module for Claude CLI provider.
 *
 * Provides two UI fields:
 * - Model selector (Haiku/Sonnet/Opus)
 * - Integration method selector (Memory File/Session)
 */
val claudeCliSettings = object : ProviderSettingsModule {
    override val providerId = PROVIDER_ID

    override val stateDefaults = mapOf<String, Any?>(
        "model" to ClaudeModel.SONNET,
        "method" to ClaudeIntegrationMethod.SESSION
    )

    override fun buildContribution(state: PCEState): ProviderSettingsContribution {
        val isVisible = state.persistent.providerId == PROVIDER_ID
        val settings = getClaudeSettings(state)

        return ProviderSettingsContribution(
            providerId = providerId,
            title = "Claude Settings",
            visible = isVisible,
            fields = listOf(
                ProviderSettingsField(
                    id = "claudeModel",
                    type = FieldType.SELECT,
                    label = "Model",
                    options = ClaudeModel.getAvailableModels().map { desc ->
                        FieldOption(
                            value = desc.id.name,
                            label = desc.label,
                            description = desc.description
                        )
                    },
                    value = settings.model.name,
                    command = FieldCommand(
                        type = SelectClaudeModel.type,
                        payloadKey = "model"
                    )
                ),
                ProviderSettingsField(
                    id = "claudeMethod",
                    type = FieldType.SELECT,
                    label = "Method",
                    options = ClaudeIntegrationMethod.getAvailableMethods().map { desc ->
                        FieldOption(
                            value = desc.id.name,
                            label = desc.label,
                            description = desc.description
                        )
                    },
                    value = settings.method.name,
                    command = FieldCommand(
                        type = SelectClaudeMethod.type,
                        payloadKey = "method"
                    )
                )
            )
        )
    }
}
