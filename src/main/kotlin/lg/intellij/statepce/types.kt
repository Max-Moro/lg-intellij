package lg.intellij.statepce

import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionInfo
import lg.intellij.models.ShellType
import lg.intellij.models.TagSetsListSchema
import lg.intellij.statepce.domains.DEFAULT_CTX_LIMIT
import lg.intellij.statepce.domains.DEFAULT_ENCODER
import lg.intellij.statepce.domains.DEFAULT_TOKENIZER_LIB

// Re-export engine types for convenience
typealias BaseCommand = lg.intellij.stateengine.BaseCommand
typealias RuleResult = lg.intellij.stateengine.RuleResult
typealias AsyncOperation = lg.intellij.stateengine.AsyncOperation

// ============================================
// Provider Info
// ============================================

/**
 * Information about an AI provider for UI display.
 */
data class ProviderInfo(
    val id: String,
    val name: String,
    val priority: Int
)

// ============================================
// Encoder Entry
// ============================================

/**
 * Encoder entry with optional cached flag.
 *
 * The cached flag indicates if this encoder's vocabulary
 * has been downloaded and is available locally.
 */
data class EncoderEntry(
    val name: String,
    val cached: Boolean = false
)

// ============================================
// Persistent State (P) - saved between sessions
// ============================================

/**
 * Persistent State — saved between IDE sessions.
 *
 * Contains user selections and preferences that should persist:
 * - Selected provider, template, section
 * - Mode selections per context/provider
 * - Tag selections per context
 * - Tokenization settings
 * - CLI settings
 * - Provider-specific settings
 *
 * Will be used in PCEStateStore with SimplePersistentStateComponent.
 */
data class PersistentState(
    var providerId: String = "",
    var template: String = "",
    var section: String = "",

    /**
     * Modes by context and provider.
     * Structure: [contextName][providerId][modeSetId] = modeId
     */
    var modesByContextProvider: MutableMap<String, MutableMap<String, MutableMap<String, String>>> = mutableMapOf(),

    /**
     * Tags by context.
     * Structure: [contextName][tagSetId] = Set<tagId>
     */
    var tagsByContext: MutableMap<String, MutableMap<String, MutableSet<String>>> = mutableMapOf(),

    // Tokenization settings
    var tokenizerLib: String = DEFAULT_TOKENIZER_LIB,
    var encoder: String = DEFAULT_ENCODER,
    var ctxLimit: Int = DEFAULT_CTX_LIMIT,

    // CLI settings
    var cliScope: String = "",
    var cliShell: ShellType = ShellType.getDefault(),

    // Git settings
    var targetBranch: String = "",

    // Task text
    var taskText: String = "",

    /**
     * Dynamic provider settings.
     * Structure: [providerId][settingKey] = settingValue
     *
     * Allows providers to store their specific settings
     * (e.g., Claude model, Codex reasoning effort) without
     * requiring changes to this class.
     */
    var providerSettings: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()
)

// ============================================
// Configuration State (C) - loaded from CLI
// ============================================

/**
 * Configuration State — loaded from CLI.
 *
 * Contains catalog data retrieved from CLI commands:
 * - Available contexts and sections
 * - Mode-sets and tag-sets definitions
 * - Tokenizer libraries and encoders
 *
 * This state is read-only from UI perspective —
 * it can only be refreshed by reloading from CLI.
 */
data class ConfigurationState(
    var contexts: List<String> = emptyList(),
    var sections: List<SectionInfo> = emptyList(),
    var modeSets: ModeSetsListSchema = ModeSetsListSchema(emptyList()),
    var tagSets: TagSetsListSchema = TagSetsListSchema(emptyList()),
    var tokenizerLibs: List<String> = emptyList(),
    var encoders: List<EncoderEntry> = emptyList()
)

// ============================================
// Environment State (E) - detected at startup
// ============================================

/**
 * Environment State — detected at startup.
 *
 * Contains runtime environment information:
 * - Available AI providers (detected via plugin dependencies)
 * - Git branches (from current repository)
 *
 * This state is re-detected on certain events
 * (e.g., Git branch changes, plugin enable/disable).
 */
data class EnvironmentState(
    var providers: List<ProviderInfo> = emptyList(),
    var branches: List<String> = emptyList()
)

// ============================================
// Combined PCE State (Business State Only)
// ============================================

/**
 * Combined business state for LG Plugin.
 *
 * PCE = Persistent + Configuration + Environment
 *
 * IMPORTANT: Does NOT contain coordination fields (isStable, pendingOps).
 * Those are managed by StateCoordinator and exposed via UIMeta.
 *
 * This separation ensures:
 * 1. Business state is decoupled from coordination logic
 * 2. UI can subscribe to business state without loading concerns
 * 3. Coordinator can manage stability independently
 */
data class PCEState(
    val persistent: PersistentState,
    val configuration: ConfigurationState,
    val environment: EnvironmentState
)

// ============================================
// Default State Factories
// ============================================

/**
 * Creates default ConfigurationState (empty catalogs).
 */
fun createDefaultConfigurationState(): ConfigurationState = ConfigurationState()

/**
 * Creates default EnvironmentState (empty providers/branches).
 */
fun createDefaultEnvironmentState(): EnvironmentState = EnvironmentState()

