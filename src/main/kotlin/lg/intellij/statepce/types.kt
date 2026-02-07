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
    val providerId: String = "",
    val template: String = "",
    val section: String = "",

    /**
     * Modes by context and provider.
     * Structure: [contextName][providerId][modeSetId] = modeId
     */
    val modesByContextProvider: Map<String, Map<String, Map<String, String>>> = emptyMap(),

    /**
     * Tags by context.
     * Structure: [contextName][tagSetId] = Set<tagId>
     */
    val tagsByContext: Map<String, Map<String, Set<String>>> = emptyMap(),

    // Tokenization settings
    val tokenizerLib: String = DEFAULT_TOKENIZER_LIB,
    val encoder: String = DEFAULT_ENCODER,
    val ctxLimit: Int = DEFAULT_CTX_LIMIT,

    // CLI settings
    val cliScope: String = "",
    val cliShell: ShellType = ShellType.getDefault(),

    // Git settings
    val targetBranch: String = "",

    // Task text
    val taskText: String = "",

    /**
     * Dynamic provider settings.
     * Structure: [settingsKey][settingKey] = settingValue
     */
    val providerSettings: Map<String, Map<String, Any?>> = emptyMap()
)

// ============================================
// PersistentState Mutation Helpers
// ============================================

/**
 * Returns a copy with a single mode updated.
 */
fun PersistentState.withMode(
    ctx: String,
    provider: String,
    modeSetId: String,
    modeId: String
): PersistentState {
    val ctxModes = modesByContextProvider[ctx]?.toMutableMap() ?: mutableMapOf()
    val providerModes = ctxModes[provider]?.toMutableMap() ?: mutableMapOf()
    providerModes[modeSetId] = modeId
    ctxModes[provider] = providerModes
    return copy(modesByContextProvider = modesByContextProvider + (ctx to ctxModes))
}

/**
 * Returns a copy with all modes for a context/provider replaced.
 */
fun PersistentState.withModes(
    ctx: String,
    provider: String,
    modes: Map<String, String>
): PersistentState {
    val ctxModes = modesByContextProvider[ctx]?.toMutableMap() ?: mutableMapOf()
    ctxModes[provider] = modes
    return copy(modesByContextProvider = modesByContextProvider + (ctx to ctxModes))
}

/**
 * Returns a copy with tags for a context replaced.
 */
fun PersistentState.withContextTags(
    ctx: String,
    tags: Map<String, Set<String>>
): PersistentState {
    return copy(tagsByContext = tagsByContext + (ctx to tags))
}

/**
 * Returns a copy with a single provider setting updated.
 */
fun PersistentState.withProviderSetting(
    settingsKey: String,
    key: String,
    value: Any?
): PersistentState {
    val currentSettings = providerSettings[settingsKey]?.toMutableMap() ?: mutableMapOf()
    currentSettings[key] = value
    return copy(providerSettings = providerSettings + (settingsKey to currentSettings))
}

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

