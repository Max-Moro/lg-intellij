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
 *
 * Ported from VS Code Extension's services/ai/providers/claude-cli/settings.ts
 */
package lg.intellij.services.ai.providers.claudecli

import lg.intellij.models.ClaudeIntegrationMethod
import lg.intellij.models.ClaudeModel
import lg.intellij.services.ai.FieldCommand
import lg.intellij.services.ai.FieldOption
import lg.intellij.services.ai.FieldType
import lg.intellij.services.ai.ProviderSettingsContribution
import lg.intellij.services.ai.ProviderSettingsField
import lg.intellij.services.ai.ProviderSettingsModule
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Commands
// ============================================

data class SelectClaudeModelPayload(val model: ClaudeModel)
data class SelectClaudeMethodPayload(val method: ClaudeIntegrationMethod)

val SelectClaudeModel = command("provider.claude-cli/SELECT_MODEL").payload<SelectClaudeModelPayload>()
val SelectClaudeMethod = command("provider.claude-cli/SELECT_METHOD").payload<SelectClaudeMethodPayload>()

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

/**
 * Creates updated providerSettings map with Claude settings changes.
 */
private fun updateClaudeSettings(
    state: PCEState,
    updates: Map<String, Any?>
): MutableMap<String, MutableMap<String, Any?>> {
    val newProviderSettings = state.persistent.providerSettings.toMutableMap()
    val claudeSettings = (newProviderSettings[SETTINGS_KEY]?.toMutableMap()) ?: mutableMapOf()

    for ((key, value) in updates) {
        claudeSettings[key] = value
    }

    newProviderSettings[SETTINGS_KEY] = claudeSettings
    return newProviderSettings
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
        condition = { _: PCEState, _: SelectClaudeModelPayload -> true },
        apply = { state: PCEState, payload: SelectClaudeModelPayload ->
            lgResult(
                mutations = mapOf(
                    "providerSettings" to updateClaudeSettings(state, mapOf("model" to payload.model))
                )
            )
        }
    ))

    // When Claude method selected, update provider settings
    rule.invoke(SelectClaudeMethod, RuleConfig(
        condition = { _: PCEState, _: SelectClaudeMethodPayload -> true },
        apply = { state: PCEState, payload: SelectClaudeMethodPayload ->
            lgResult(
                mutations = mapOf(
                    "providerSettings" to updateClaudeSettings(state, mapOf("method" to payload.method))
                )
            )
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
