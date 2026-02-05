package lg.intellij.statepce

import lg.intellij.stateengine.BusinessRule
import lg.intellij.stateengine.RuleRegistry
import lg.intellij.stateengine.createRuleFactory

/**
 * LG Extension Rule Factory
 *
 * Uses createRuleFactory from state-engine with LG-specific types.
 * All domain rules register themselves via this factory.
 *
 * Ported from VS Code Extension's state-lg/rule.ts
 */

// Registry for LG rules (module-level singleton)
private val lgRuleRegistry = RuleRegistry<PCEState, LGRuleResult>()

/**
 * Define a business rule for LG Extension.
 *
 * Auto-registers in the global LG rule registry.
 *
 * Example:
 * ```kotlin
 * rule(SelectContext, RuleConfig(
 *     condition = { state, payload -> payload.template != state.persistent.template },
 *     apply = { state, payload -> lgResult(
 *         mutations = mapOf("template" to payload.template),
 *         asyncOps = listOf(...)
 *     )}
 * ))
 * ```
 */
val rule = createRuleFactory(lgRuleRegistry)

/**
 * Get all registered LG rules.
 */
fun getAllRules(): List<BusinessRule<PCEState, LGRuleResult>> {
    return lgRuleRegistry.getAll()
}
