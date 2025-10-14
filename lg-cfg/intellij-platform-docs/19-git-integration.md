# Git Integration (VCS API)

## Обзор

IntelliJ Platform предоставляет API для работы с системами контроля версий (VCS), включая Git.

**Для LG plugin:** минимальная Git интеграция нужна для:
- Получения списка веток (для target-branch selector)
- Определения изменённых файлов (для branch-changes mode)

## Git Extension API

### Зависимость

```kotlin
// build.gradle.kts
dependencies {
    intellijPlatform {
        bundledPlugin("com.intellij.modules.vcs.git")
    }
}
```

```xml
<!-- plugin.xml -->
<depends optional="true" config-file="withGit.xml">
    Git4Idea
</depends>
```

### Проверка доступности Git

```kotlin
import git4idea.GitUtil

fun isGitAvailable(project: Project): Boolean {
    return try {
        val repos = GitUtil.getRepositoryManager(project).repositories
        repos.isNotEmpty()
    } catch (e: Exception) {
        false
    }
}
```

## GitRepository

```kotlin
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

fun getRepository(project: Project): GitRepository? {
    val manager = GitRepositoryManager.getInstance(project)
    
    // Все репозитории проекта
    val repos = manager.repositories
    
    // Первый репозиторий (для simple проектов)
    return repos.firstOrNull()
}
```

## Branches API

### Получение веток

```kotlin
import git4idea.branch.GitBranchUtil

suspend fun getAllBranches(project: Project): List<String> {
    return withContext(Dispatchers.IO) {
        readAction {
            val repo = getRepository(project) ?: return@readAction emptyList()
            
            // Local branches
            val localBranches = repo.branches.localBranches.map { it.name }
            
            // Remote branches
            val remoteBranches = repo.branches.remoteBranches.map { it.name }
            
            (localBranches + remoteBranches).distinct().sorted()
        }
    }
}

fun getCurrentBranch(project: Project): String? {
    return readAction {
        val repo = getRepository(project) ?: return@readAction null
        repo.currentBranch?.name
    }
}
```

### Branch Operations (опционально)

```kotlin
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

fun checkoutBranch(project: Project, branchName: String) {
    val repo = getRepository(project) ?: return
    
    object : Task.Backgroundable(project, "Checking out $branchName...", true) {
        override fun run(indicator: ProgressIndicator) {
            val handler = GitLineHandler(
                project,
                repo.root,
                GitCommand.CHECKOUT
            )
            handler.addParameters(branchName)
            
            val result = Git.getInstance().runCommand(handler)
            
            if (!result.success()) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        result.errorOutputAsJoinedString,
                        "Checkout Failed"
                    )
                }
            }
        }
    }.queue()
}
```

## Changed Files

### Get changed files (для branch-changes mode)

```kotlin
import com.intellij.openapi.vcs.changes.ChangeListManager

suspend fun getChangedFiles(project: Project): List<VirtualFile> {
    return readAction {
        val changeListManager = ChangeListManager.getInstance(project)
        
        // Все изменённые файлы
        changeListManager.affectedFiles
    }
}

suspend fun getChangedFilesBetweenBranches(
    project: Project,
    targetBranch: String
): List<VirtualFile> {
    return withContext(Dispatchers.IO) {
        readAction {
            val repo = getRepository(project) ?: return@readAction emptyList()
            val currentBranch = repo.currentBranch?.name ?: return@readAction emptyList()
            
            // Получить diff между ветками
            val changes = GitChangeUtils.getDiff(
                project,
                repo.root,
                currentBranch,
                targetBranch,
                null
            )
            
            changes.mapNotNull { it.virtualFile }
        }
    }
}
```

## Simple Git Service для LG

```kotlin
@Service(Service.Level.PROJECT)
class LgGitService(private val project: Project) {
    
    fun isGitRepository(): Boolean {
        return try {
            getRepository() != null
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun getBranches(): List<String> {
        if (!isGitRepository()) {
            return emptyList()
        }
        
        return withContext(Dispatchers.IO) {
            readAction {
                val repo = getRepository() ?: return@readAction emptyList()
                
                val local = repo.branches.localBranches.map { it.name }
                val remote = repo.branches.remoteBranches.map { it.name }
                
                (local + remote).distinct().sorted()
            }
        }
    }
    
    fun getCurrentBranch(): String? {
        return readAction {
            getRepository()?.currentBranch?.name
        }
    }
    
    suspend fun getChangedFiles(): List<VirtualFile> {
        return readAction {
            val manager = ChangeListManager.getInstance(project)
            manager.affectedFiles
        }
    }
    
    private fun getRepository(): GitRepository? {
        val manager = GitRepositoryManager.getInstance(project)
        return manager.repositories.firstOrNull()
    }
    
    companion object {
        fun getInstance(project: Project) = project.service<LgGitService>()
    }
}
```

## UI для Branch Selection

### ComboBox с ветками

```kotlin
class ControlPanel(private val project: Project) : JPanel() {
    
    private val branchComboBox = ComboBox<String>()
    private val scope = CoroutineScope(SupervisorJob())
    
    init {
        layout = BorderLayout()
        add(createContent(), BorderLayout.CENTER)
        
        loadBranches()
    }
    
    private fun createContent() = panel {
        row("Target Branch:") {
            cell(branchComboBox)
                .align(AlignX.FILL)
                .visibleIf(object : ComponentPredicate() {
                    override fun invoke(): Boolean {
                        // Показывать только в review mode
                        return isReviewMode()
                    }
                    
                    override fun addListener(listener: (Boolean) -> Unit) {
                        // Listen to mode changes
                    }
                })
        }
    }
    
    private fun loadBranches() {
        val gitService = project.service<LgGitService>()
        
        if (!gitService.isGitRepository()) {
            return
        }
        
        scope.launch {
            val branches = gitService.getBranches()
            
            withContext(Dispatchers.EDT) {
                branchComboBox.removeAllItems()
                branches.forEach { branchComboBox.addItem(it) }
                
                // Выбрать текущую ветку
                val current = gitService.getCurrentBranch()
                if (current != null) {
                    branchComboBox.selectedItem = current
                }
            }
        }
    }
}
```

## VCS Integration Patterns

### Check if file is under VCS

```kotlin
import com.intellij.openapi.vcs.FileStatusManager

fun isUnderVcs(file: VirtualFile, project: Project): Boolean {
    val status = FileStatusManager.getInstance(project).getStatus(file)
    return status != FileStatus.UNKNOWN && 
           status != FileStatus.NOT_CHANGED
}
```

### Get file modification status

```kotlin
import com.intellij.openapi.vcs.FileStatus

val status = FileStatusManager.getInstance(project).getStatus(file)

when (status) {
    FileStatus.MODIFIED -> "Modified"
    FileStatus.ADDED -> "Added"
    FileStatus.DELETED -> "Deleted"
    FileStatus.NOT_CHANGED -> "Unchanged"
    FileStatus.UNKNOWN -> "Not in VCS"
    else -> status.text
}
```

## Для LG Plugin (минимальная интеграция)

### Используемые функции

```kotlin
@Service(Service.Level.PROJECT)
class LgGitIntegration(private val project: Project) {
    
    /**
     * Проверить наличие Git в проекте
     */
    fun hasGit(): Boolean {
        return project.service<LgGitService>().isGitRepository()
    }
    
    /**
     * Получить список веток для UI selector
     */
    suspend fun getBranchesForUI(): List<String> {
        return project.service<LgGitService>().getBranches()
    }
    
    /**
     * Передать target-branch в CLI
     */
    fun getTargetBranchArg(): List<String> {
        val state = project.service<LgStateService>()
        val targetBranch = state.state.targetBranch
        
        return if (targetBranch.isNotBlank()) {
            listOf("--target-branch", targetBranch)
        } else {
            emptyList()
        }
    }
}
```

### В CLI Service

```kotlin
suspend fun generateWithBranchMode(
    section: String,
    targetBranch: String?
): String {
    val args = buildList {
        add("listing-generator")
        add("render")
        add("sec:$section")
        add("--mode")
        add("vcs_mode:branch-changes")
        
        if (!targetBranch.isNullOrBlank()) {
            add("--target-branch")
            add(targetBranch)
        }
        
        // ... остальные аргументы
    }
    
    return execute(args)
}
```

## Optional: Full Git Integration

Если в будущем понадобится полная Git интеграция:

```kotlin
import git4idea.commands.*
import git4idea.repo.GitRepository

class AdvancedGitService(private val project: Project) {
    
    fun getDiff(
        fromBranch: String,
        toBranch: String
    ): List<Change> {
        val repo = getRepository() ?: return emptyList()
        
        // Execute git diff
        val handler = GitLineHandler(
            project,
            repo.root,
            GitCommand.DIFF
        )
        handler.addParameters("--name-only")
        handler.addParameters("$fromBranch..$toBranch")
        
        val result = Git.getInstance().runCommand(handler)
        
        if (!result.success()) {
            return emptyList()
        }
        
        return parseChanges(result.output)
    }
}
```

Но **для LG plugin это избыточно** — достаточно передать `--target-branch` в CLI, остальное делает Python код.
