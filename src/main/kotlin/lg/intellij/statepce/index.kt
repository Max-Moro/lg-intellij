/**
 * State PCE Layer - Public API
 *
 * This file documents the public API of the statepce package.
 *
 * Public surface:
 * - types.kt: PCEState, PersistentState, ConfigurationState, EnvironmentState, ProviderInfo, EncoderEntry
 * - store.kt: PCEStateStore, LGRuleResult, lgResult(), emptyLgResult()
 * - rule.kt: rule (factory), getAllRules()
 * - coordinator.kt: PCEStateCoordinator (typealias), createPCECoordinator(project)
 * - domains/: Business rule modules (lifecycle, provider, context, section, adaptive, tokenization)
 *
 * Usage:
 * ```
 * import lg.intellij.statepce.*
 * ```
 */
@file:Suppress("unused")

package lg.intellij.statepce

// All types are exported directly from their respective files.
// This file exists for documentation.
