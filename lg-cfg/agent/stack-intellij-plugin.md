# Technology Stack Recommendations

This project is developing a plugin for **IntelliJ Platform**. It's a fairly traditional project structure for these purposes.

## Stack

- **Platform**: IntelliJ IDEA Community 2025.2.3
- **JVM**: 21 (toolchain)
- **Gradle**: 9.0.0
- **Kotlin**: see `libs.versions.toml`
- **IntelliJ Platform Gradle Plugin**: 2.x

## Architecture

**Pattern:** Layered service-oriented architecture with reactive UI state management.

### Layers

**CLI Integration** (`lg/intellij/cli/`)
- `CliExecutor` — asynchronous execution of external CLI `lg` with timeout/stdin
- `CliResolver` — executable file search (venv → system Python → pipx)
- `CliResult` — sealed class for type-safe result processing

**Services** (`lg/intellij/services/`)
- **Generation**: `LgGenerationService` (rendering), `LgStatsService` (tokens)
- **Catalog**: `LgCatalogService` (sections/contexts/modes/tags), `TokenizerCatalogService` (libraries/encoders)
- **State**:
    - `LgSettingsService` — app-level (CLI path, AI provider)
    - `LgPanelStateService` — project-level UI (selected section/template, modes, tags, task text) with `StateFlow`
    - `LgWorkspaceStateService` — UI layout (display modes, splitter positions)
- **Diagnostics/Git**: `LgDiagnosticsService`, `LgGitService`, `LgErrorReportingService`

**Actions** (`lg/intellij/actions/`)
- Base `LgGenerateAction` parameterized by `GenerationTarget` (SECTION/CONTEXT)
- Actions → background tasks → CLI execution → parse JSON → open in editor

**UI** (`lg/intellij/ui/`)
- Tool Window: vertical split (Control Panel + Included Files)
- Panels: `LgControlPanel`, `LgModeSetsPanel`, `LgIncludedFilesPanel`
- Custom components: `LgTaskTextField`, `LgEncoderCompletionField`, `LgGroupedTable`

### State Management

- Reactive: `StateFlow` for task text synchronization between panels and dialogs
- Persistent: `BaseState` properties → `workspace.xml` (panel state) / `lg-settings.xml` (app settings)
- Effective values: Panel state with fallback to application defaults

## Important Features

**Kotlin Coroutines**
- All CLI operations — suspend functions
- UI updates via `coroutineScope.launch`

**Prohibition on object**
- DO NOT use Kotlin `object` for classes in `plugin.xml` (DI issues)
- Only `class` for Services, Actions, etc.

**VFS Listeners**
- `LgConfigFileListener` — auto-reload catalogs when `lg-cfg/` changes

**AI Integration**
- Optional integration via plugins: JetBrains AI, GitHub Copilot, Junie
- Manual sandbox installation, persists between runs

## Testing

The project has no automated tests. Testing is done by the user through running IntelliJ IDEA (Development Instance) with the installed plugin.
<!-- lg:if tag:claude-code -->
## File Paths

This project runs on Windows. When using Read/Edit/Write tools, always use **backslashes** (`\`) in file paths.
<!-- lg:endif -->