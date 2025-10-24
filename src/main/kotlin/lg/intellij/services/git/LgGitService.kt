package lg.intellij.services.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Service for Git integration.
 * 
 * Provides access to Git repository information for target branch selection.
 * Gracefully degrades when Git is not available or project is not under Git.
 * 
 * Lifecycle: Project-level (one instance per open project)
 */
@Service(Service.Level.PROJECT)
class LgGitService(private val project: Project) {
    
    /**
     * Checks if Git is available and project has Git repository.
     * 
     * @return true if Git is available, false otherwise
     */
    fun isGitAvailable(): Boolean {
        return try {
            val repository = getRepository()
            repository != null
        } catch (_: NoClassDefFoundError) {
            // Git4Idea plugin not available
            LOG.debug("Git4Idea plugin not available")
            false
        } catch (e: Exception) {
            LOG.debug("Git not available: ${e.message}")
            false
        }
    }
    
    /**
     * Gets list of all branches (local and remote).
     * 
     * @return List of branch names, empty if Git is not available
     */
    suspend fun getBranches(): List<String> {
        if (!isGitAvailable()) {
            return emptyList()
        }
        
        return try {
            withContext(Dispatchers.IO) {
                val repository = getRepository() ?: return@withContext emptyList()
                
                // Get local branches
                val localBranches = repository.branches.localBranches.mapNotNull { it.name }
                
                // Get remote branches
                val remoteBranches = repository.branches.remoteBranches.mapNotNull { it.name }
                
                // Combine and deduplicate
                (localBranches + remoteBranches).distinct().sorted()
            }
        } catch (e: Exception) {
            LOG.error("Failed to get branches", e)
            emptyList()
        }
    }

    /**
     * Gets Git repository for the project.
     * 
     * @return GitRepository or null if not available
     */
    private fun getRepository(): GitRepository? {
        return try {
            val manager = GitRepositoryManager.getInstance(project)
            
            // Get first repository (for simple projects)
            // In multi-repo projects, this returns the first one
            manager.repositories.firstOrNull()
        } catch (e: Exception) {
            LOG.debug("Failed to get repository: ${e.message}")
            null
        }
    }
    
    companion object {
        private val LOG = logger<LgGitService>()
        
        /**
         * Gets the service instance for the project.
         */
        fun getInstance(project: Project): LgGitService = project.service()
    }
}

