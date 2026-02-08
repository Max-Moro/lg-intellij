/**
 * Provider Settings Module — extensible architecture for provider-specific settings.
 *
 * Allows AI providers to define their own UI contributions
 * without modifying the central Control Panel code.
 *
 * Architecture:
 * 1. Provider creates a ProviderSettingsModule implementation
 * 2. Module defines stateDefaults and buildContribution()
 * 3. Module registers rules for its commands via rule() factory
 * 4. UI renders contributions dynamically from ProviderSettingsContribution
 */
package lg.intellij.ai

import lg.intellij.statepce.PCEState

// ============================================
// Provider Settings Module Interface
// ============================================

/**
 * Module for provider-specific settings.
 *
 * Each provider can register a settings module that defines:
 * - Default values for persistent state
 * - UI contribution builder (fields for dynamic rendering)
 * - Rules for handling settings commands (registered via rule() in module file)
 */
interface ProviderSettingsModule {
    /** Provider ID this module belongs to (e.g., "com.anthropic.claude.cli") */
    val providerId: String

    /** Default values for providerSettings[providerId] in PersistentState */
    val stateDefaults: Map<String, Any?>

    /** Build UI contribution from current PCEState */
    fun buildContribution(state: PCEState): ProviderSettingsContribution
}

// ============================================
// UI Contribution Types
// ============================================

/**
 * UI contribution from a provider settings module.
 *
 * Describes a group of settings fields to render in the Control Panel.
 * The UI renderer iterates over contributions and generates
 * appropriate Swing components for each field.
 */
data class ProviderSettingsContribution(
    /** Provider ID */
    val providerId: String,
    /** Section title (e.g., "Claude Settings") */
    val title: String,
    /** Whether this section should be visible */
    val visible: Boolean,
    /** Fields to render */
    val fields: List<ProviderSettingsField>
)

/**
 * Description of a single settings field.
 *
 * Used by the UI renderer to generate the appropriate Swing component:
 * - SELECT → ComboBox with options
 * - TEXT → JBTextField
 */
data class ProviderSettingsField(
    /** Unique field identifier */
    val id: String,
    /** Field type (determines which Swing component to use) */
    val type: FieldType,
    /** Label text displayed next to the field */
    val label: String,
    /** Options for SELECT type (ignored for TEXT) */
    val options: List<FieldOption>? = null,
    /** Current value */
    val value: String,
    /** Command to emit when field value changes */
    val command: FieldCommand
)

/**
 * Field type for provider settings.
 */
enum class FieldType {
    /** Rendered as ComboBox with options */
    SELECT,
    /** Rendered as text input field */
    TEXT
}

/**
 * Option for SELECT field type.
 */
data class FieldOption(
    /** Option value (stored in state) */
    val value: String,
    /** Display label */
    val label: String,
    /** Optional description (shown as tooltip or secondary text) */
    val description: String? = null
)

/**
 * Command descriptor for field change events.
 *
 * When a field value changes, the UI emits a command with:
 * - type: command type string (e.g., "provider.claude-cli/SELECT_MODEL")
 * - payloadKey: key name for the new value in the command payload
 *
 * Example: FieldCommand("provider.claude-cli/SELECT_MODEL", "model")
 * → emits command with payload { model: "sonnet" }
 */
data class FieldCommand(
    /** Command type to dispatch */
    val type: String,
    /** Key name for the value in command payload */
    val payloadKey: String
)

// ============================================
// Provider Settings Utilities
// ============================================

/**
 * Resolves a value from providerSettings to an enum.
 *
 * Handles both direct enum instances (in-memory after rule apply)
 * and String values (after persistence round-trip via syncToPersistentData).
 */
inline fun <reified T : Enum<T>> resolveEnum(value: Any?, default: T): T {
    return when (value) {
        is T -> value
        is String -> runCatching { enumValueOf<T>(value) }.getOrNull() ?: default
        else -> default
    }
}
