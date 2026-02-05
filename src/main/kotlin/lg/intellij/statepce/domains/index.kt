/**
 * Domain Registry — registers all domain rules.
 *
 * Called from createPCECoordinator() to ensure all business rules
 * are registered before the coordinator starts processing commands.
 *
 * Each domain provides a registerXxxRules(project) function
 * that captures project reference for async operations.
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.project.Project

/**
 * Register all domain rules.
 *
 * Must be called once before creating the coordinator.
 *
 * @param project Project for service access in async operations
 */
fun registerAllDomainRules(project: Project) {
    registerLifecycleRules(project)
    registerProviderRules(project)
    registerContextRules(project)
    registerSectionRules(project)
    registerAdaptiveRules(project)
    registerTokenizationRules(project)
}
