/**
 * Section Domain - section selection for Inspect panel.
 *
 * Commands:
 * - section/SELECT - select section from dropdown
 * - section/LOADED - store loaded sections list and validate current selection
 *
 * Sections carry their own mode-sets and tag-sets (via SectionInfo),
 * allowing per-section adaptive settings in future.
 *
 * Ported from VS Code Extension's state-lg/domains/section.ts
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.project.Project
import lg.intellij.models.SectionInfo
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Commands
// ============================================

data class SelectSectionPayload(val section: String)
val SelectSection = command("section/SELECT").payload<SelectSectionPayload>()

data class SectionsLoadedPayload(val sections: List<SectionInfo>)
val SectionsLoaded = command("section/LOADED").payload<SectionsLoadedPayload>()

// ============================================
// Rule Registration
// ============================================

/**
 * Register section domain rules.
 *
 * @param project Project for service access (unused in section rules, kept for convention)
 */
fun registerSectionRules(@Suppress("unused") project: Project) {

    // When sections loaded, validate current selection and store in config
    rule.invoke(SectionsLoaded, RuleConfig(
        condition = { _: PCEState, _: SectionsLoadedPayload -> true },
        apply = { state: PCEState, payload: SectionsLoadedPayload ->
            val sections = payload.sections
            val currentSection = state.persistent.section

            val sectionNames = sections.map { it.name }
            val isValid = currentSection.isNotBlank() && currentSection in sectionNames
            val newSection = if (isValid) currentSection else (sectionNames.firstOrNull() ?: "")

            lgResult(
                configMutations = mapOf("sections" to sections),
                mutations = if (currentSection != newSection) mapOf("section" to newSection) else null
            )
        }
    ))

    // When section changes, update persistent state
    rule.invoke(SelectSection, RuleConfig(
        condition = { state: PCEState, payload: SelectSectionPayload ->
            payload.section != state.persistent.section
        },
        apply = { _: PCEState, payload: SelectSectionPayload ->
            lgResult(mutations = mapOf("section" to payload.section))
        }
    ))
}
