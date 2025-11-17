# Architecture of Listing Generator Plugin for IntelliJ Platform

## Overview

**Listing Generator IntelliJ Plugin** — plugin for integrating CLI tool Listing Generator into JetBrains IDE ecosystem.

**Target Platform:** IntelliJ Platform 2024.1+
**Development Language:** Kotlin
**UI Toolkit:** Swing + IntelliJ UI Components + Kotlin UI DSL
**Async:** Kotlin Coroutines

**Compatibility:** IntelliJ IDEA, PyCharm, WebStorm, CLion and other IDE based on IntelliJ Platform.

---

## Key Architecture Principles

### 1. Conformance to Platform Patterns

- **Services** for encapsulation of business logic and state
- **Actions** for user commands
- **Extensions** for IDE integration
- **Message Bus** for loosely coupled communication between components
- **Disposable** for correct lifecycle and resource management

### 2. Separation of Concerns (High Cohesion, Low Coupling)

- **Presentation Layer** (UI) isolated from business logic
- **Service Layer** unaware of UI components
- **CLI Integration Layer** encapsulates all interactions with external process
- **State Management** centralized in dedicated services

### 3. Extensibility

- Clear interfaces between layers
- Extension Points for future extensions
- Modular structure allows adding functionality without rewriting existing code

### 4. Asynchronous and Production-Ready

- Kotlin Coroutines for all long-running operations
- Correct thread management (EDT for UI, BGT for reading, IO for external processes)
- Cancellation support for all background operations
- Structured concurrency via Service-injected CoroutineScope

---

## Project Structure

```
lg-intellij/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/lg/intellij/
│   │   │   ├── actions/              # User commands
│   │   │   ├── cli/                  # CLI integration
│   │   │   ├── services/             # Business logic
│   │   │   │   ├── core/             # Core services
│   │   │   │   ├── catalog/          # Catalog of sections/contexts
│   │   │   │   ├── generation/       # Content generation
│   │   │   │   ├── state/            # State management
│   │   │   │   ├── vfs/              # Virtual File System integration
│   │   │   │   └── ai/               # AI integration
│   │   │   ├── ui/                   # UI components
│   │   │   │   ├── toolwindow/       # Tool Window panels
│   │   │   │   ├── dialogs/          # Dialogs
│   │   │   │   ├── components/       # Reusable components
│   │   │   │   └── renderers/        # Tree/List renderers
│   │   │   ├── settings/             # Plugin settings
│   │   │   ├── git/                  # Git integration
│   │   │   ├── listeners/            # Event listeners
│   │   │   ├── models/               # Data models
│   │   │   └── utils/                # Utilities
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └── plugin.xml        # Plugin configuration
│   │       ├── messages/
│   │       │   └── LgBundle.properties  # i18n
│   │       └── icons/
│   │           └── lg-icon.svg
│   └── test/
│       ├── kotlin/
│       └── resources/
│           └── testData/
└── README.md
```

---

## Main Architectural Layers

### Layer 1: CLI Integration (Lower Layer)

**Responsibility:** isolation of all interaction with external CLI process.

#### `cli/CliExecutor`
- Single point for CLI command execution
- Process lifecycle management (start, stop, timeout)
- Stdout/stderr capturing
- Execution error handling
- Stdin support for task text transmission
- Cancellation support via Kotlin Coroutines

#### `cli/CliResolver`
- Detection of listing-generator executable location
- Resolution strategies: explicit path (from settings) → system PATH → managed venv → Python module
- Caching of resolved path
- Cache invalidation on settings change

#### `cli/CliResponseParser`
- Parsing JSON responses from CLI
- Mapping to Kotlin data classes
- Parsing error handling
- Protocol versioning

**Dependencies:** IntelliJ Platform Process API (`GeneralCommandLine`, `CapturingProcessHandler`)

**Consumers:** Service Layer

---

### Layer 2: Service Layer (Business Logic)

All Services are implemented as **Light Services** with `@Service` annotation. Project-level services receive injected `CoroutineScope` for asynchronous operations.

#### Core Services

##### `services/core/LgCatalogService` (Project-level)
- Loading lists of sections, contexts, mode-sets, tag-sets from CLI
- In-memory caching of lists
- Automatic invalidation on lg-cfg/ changes (via VFS listener)
- Reactive exposure via Kotlin Flow (`StateFlow<List<String>>`)
- Methods: `getSections()`, `getContexts()`, `getModeSets()`, `getTagSets()`, `reload()`

##### `services/core/LgGenerationService` (Project-level)
- Generation of listings and contexts via CLI
- Management of generation parameters (tokenizer, encoder, modes, tags, task)
- Progress reporting via IntelliJ Platform Progress API
- Methods: `generateListing()`, `generateContext()`, `generateReport()` (return suspending functions)

##### `services/core/LgDiagnosticsService` (Project-level)
- Running diagnostics via `lg diag`
- Cache reset via `lg diag --rebuild-cache`
- Building diagnostic bundle
- Methods: `runDiagnostics()`, `rebuildCache()`, `buildBundle()`

#### State Management Services

##### `services/state/LgPanelStateService` (Project-level)
- Saving Control Panel state (selected section/template, tokenization params, modes, tags, task text, target branch)
- Implements `PersistentStateComponent` via `SimplePersistentStateComponent`
- Storage: workspace file (not committed to VCS)
- Reactive state exposure via `StateFlow`

##### `services/state/LgSettingsService` (Application-level)
- Global plugin settings (CLI path, Python interpreter, install strategy, default tokenizer/encoder/ctx-limit, AI provider, openAsEditable flag)
- Implements `PersistentStateComponent`
- Storage: application config directory, roaming enabled
- Methods: `getInstance()` (singleton getter)

##### `services/state/LgWorkspaceStateService` (Project-level)
- Workspace-specific UI state (tree view mode for Included Files, last selected values, window positions)
- Storage: workspace file
- Not synchronized between machines

#### Catalog Services

##### `services/catalog/TokenizerCatalogService` (Application-level)
- Loading list of tokenizer libraries and encoders via CLI
- Caching with TTL
- Determining cached vs available encoders
- Methods: `getLibraries()`, `getEncoders(lib: String)`, `invalidate()`

##### `services/catalog/GitBranchCatalogService` (Project-level)
- Integration with Git4Idea API for getting branch list
- Optional dependency on Git plugin
- Graceful degradation if Git is unavailable
- Methods: `isGitAvailable()`, `getBranches()`, `getCurrentBranch()`

#### Generation Services

##### `services/generation/LgContextGenerator` (Project-level)
- Generation of contexts considering all parameters
- Mapping UI state → CLI arguments
- Task text handling (inline, from file, from stdin)
- Returns typed result (`GenerationResult` data class)

##### `services/generation/LgListingGenerator` (Project-level)
- Generation of listings for sections
- Similar to `LgContextGenerator` but for sections

##### `services/generation/LgStatsCollector` (Project-level)
- Getting statistics via `lg report`
- Parsing ReportSchema JSON
- Providing typed models for UI

#### AI Integration

##### `services/ai/AiIntegrationService` (Application-level)
- Central service for AI providers
- Registry pattern for providers (clipboard, local copilots — JetBrains AI Assistant + Junie, openai.api and others)
- Detection of available providers on startup
- Single point for sending content to AI
- Methods: `detectProviders()`, `sendTo(providerId, content)`, `getAvailableProviders()`

##### `services/ai/providers/*` (Package with providers)
- Implementations for each AI provider
- Common interface `AiProvider`
- Base classes: `CliBasedProvider`, `ApiBasedProvider`, `ExtensionBasedProvider`
- Graceful degradation if provider is unavailable

**Dependencies:** CLI Integration Layer, Settings Services

**Consumers:** UI Layer, Actions

---

### Layer 3: UI Layer

#### Tool Window

##### `ui/toolwindow/LgToolWindowFactory`
- Implements `ToolWindowFactory` with `DumbAware`
- Creates Tool Window with two tabs: Control Panel and Included Files
- Initialization: setting tool window properties (anchor, icon, stripe title)
- Conditional visibility via `isApplicableAsync()` — show only if lg-cfg/ exists in project

##### `ui/toolwindow/LgControlPanel`
- Main control panel (first tab)
- Inherits from `SimpleToolWindowPanel`
- Contains: template selector, section selector, task input field, tokenization settings (library/encoder/ctx-limit), adaptive settings (mode-sets, tag-sets, target-branch selector), action buttons
- Built using **Kotlin UI DSL**
- Integration with `LgPanelStateService` for persistence
- Reactive updates via subscription to StateFlow from catalog services
- Toolbar with actions: Refresh, Settings, Help

##### `ui/toolwindow/LgIncludedFilesPanel`
- Second tab of Tool Window
- Tree view with support for flat/tree modes
- Uses standard IntelliJ `Tree` component
- Custom `TreeCellRenderer` for displaying files with icons
- Double-click opens file in editor
- Context menu: Open, Copy Path, Refresh
- Toggle view mode via dedicated Action in toolbar
- State (view mode) stored in `LgWorkspaceStateService`

#### Dialogs

##### `ui/dialogs/LgStatsDialog`
- Inherits from `DialogWrapper`
- Displays detailed statistics per ReportSchema
- Contains: summary cards (files count, tokens, size), grouped table with files (with filtering and sorting), adapter metrics (collapsible sections)
- Toolbar: Refresh, Send to AI, Generate
- Copy to clipboard action
- Task text input field (integrated, like in VS Code Stats webview)
- Built using Swing components and partially Kotlin UI DSL

##### `ui/dialogs/LgDoctorDialog`
- Displays diagnostic results
- Contains: config status, cache status, environment info, checks table, applied migrations list
- Actions: Refresh, Rebuild Cache, Build Bundle, Copy JSON
- Raw JSON viewer in collapsible section

##### `ui/dialogs/LgInitWizardDialog` (Optional)
- Wizard for creating lg-cfg/ via `lg init`
- Multi-step dialog with preset selection
- Conflict resolution UI
- Can be simplified to simple dialog with ComboBox

#### UI Components (Reusable)

##### `ui/components/LgComboBoxWithAutoComplete`
- Wrapper over `ComboBox` with filtering and autocomplete support
- For encoder selector (custom values support)
- Display of cached items (badge or icon)

##### `ui/components/LgTaskInputField`
- Custom component for task description input
- Multi-line expandable text (similar to chat input in VS Code)
- Can be implemented via `JTextArea` with custom sizing logic or `EditorTextField`

##### `ui/components/LgGroupedTable`
- Table with support for grouping by directories (similar to grouped table in VS Code Stats)
- Hierarchical grouping level control (slider ←N→ ∞)
- Filtering and sorting
- Can be implemented via `JBTable` with custom `TableModel`

##### `ui/components/LgModeSetsPanel`
- Panel with dynamically generated ComboBox for each mode-set
- Layout via Kotlin UI DSL
- Two-way binding with `LgPanelStateService`

##### `ui/components/LgTagSetsPanel`
- Panel with checkboxes for tag selection
- Organization in collapsible groups by tag-set
- ScrollPane for large number of tags
- Can be implemented as separate Dialog or inline panel

##### `ui/renderers/FileTreeCellRenderer`
- Custom `ColoredTreeCellRenderer` for file tree
- Display files with icons by file type
- Display directories with folder icons
- Optional display of metadata (size, modified status)

---

### Layer 4: Actions

Actions are registered in `plugin.xml` and used in menus, toolbars, keyboard shortcuts.

#### Main Actions

##### `actions/LgGenerateListingAction`
- Generation of listing for selected section
- Getting parameters from `LgPanelStateService`
- Calling `LgListingGenerator` service
- Display result via `LgVirtualFileService`
- Keyboard shortcut: Ctrl+Shift+G (tentative)

##### `actions/LgGenerateContextAction`
- Generation of context for selected template
- Similar to `LgGenerateListingAction` but for contexts
- Keyboard shortcut: Ctrl+Shift+C (tentative)

##### `actions/LgShowStatsAction`
- Opening `LgStatsDialog` with detailed statistics
- Selection: stats for section or for context (depending on Control Panel selection)

##### `actions/LgShowIncludedFilesAction`
- Loading list of included files via CLI
- Updating Included Files tab
- Automatic switch to this tab in Tool Window

##### `actions/LgSendToAiAction`
- Generation of content (listing or context)
- Sending via `AiIntegrationService`
- Error handling and fallback to clipboard

##### `actions/LgCreateStarterConfigAction`
- Running wizard for `lg init`
- Integration with `LgInitWizardDialog`
- Opening sections.yaml after creation

##### `actions/LgOpenConfigAction`
- Opening lg-cfg/sections.yaml in editor
- Fallback: offering to create via `LgCreateStarterConfigAction` if not exists

##### `actions/LgRunDoctorAction`
- Running diagnostics
- Opening `LgDoctorDialog` with results

##### `actions/LgResetCacheAction`
- Resetting LG cache via diagnostics service
- Confirmation dialog before execution

##### `actions/LgRefreshCatalogsAction`
- Forced reload of lists from CLI (sections, contexts, encoders, branches)
- UI update after loading

#### Toggle Actions

##### `actions/LgToggleTreeViewModeAction`
- Toggling between flat and tree modes for Included Files
- Updating state in `LgWorkspaceStateService`
- Updating UI panel

#### Toolbar Action Groups

Actions are grouped in `DefaultActionGroup` for use in toolbars:
- Control Panel Toolbar Group: Refresh, Settings, Help
- Included Files Toolbar Group: Refresh, Toggle View Mode
- Stats Dialog Toolbar Group: Refresh, Generate, Send to AI

**Dependencies:** Service Layer

**Consumers:** registered in `plugin.xml`, invoked by IDE

---

### Layer 5: Settings & Configuration

#### `settings/LgSettingsConfigurable`
- Implements `BoundConfigurable`
- Application-level settings
- UI built via Kotlin UI DSL
- Sections:
  - **CLI Configuration**: CLI Path, Python Interpreter, Install Strategy
  - **Tokenization Defaults**: Default Library, Default Encoder, Default Context Limit
  - **Editor Behavior**: Open As Editable checkbox
  - **AI Integration**: AI Provider selector, OpenAI API Key configuration

#### `settings/LgProjectSettingsConfigurable` (Optional)
- Project-level settings
- Sections:
  - **Defaults**: Default Section, Default Template
  - **Modes**: Default mode for each mode-set

May be redundant at first stage, as these values are saved in `LgPanelStateService`.

**Dependencies:** `LgSettingsService`, `LgPanelStateService`

**Integration:** registration via `<applicationConfigurable>` in `plugin.xml`

---

### Layer 6: Virtual Files (Similar to VirtualDocProvider in VS Code)

#### `vfs/LgVirtualFileService` (Project-level)
- Management of creation and display of generated content
- Two modes of operation (depending on `openAsEditable` setting):
  1. **Virtual mode**: `LightVirtualFile` in memory (read-only)
  2. **Editable mode**: temporary file on disk in system temp directory
- Determining `FileType` for syntax highlighting (Markdown, YAML, JSON)
- Methods: `openListing()`, `openContext()`, `openStats()` (accept content and filename)

**Differences from VS Code:**
- VS Code uses custom URI scheme (`lg://`)
- IntelliJ uses `LightVirtualFile` (supported by platform out of the box)
- `LightVirtualFile` automatically gets syntax highlighting by `FileType`

**Dependencies:** IntelliJ Platform VFS API, `FileEditorManager`

**Consumers:** Actions, Generation Services

---

### Layer 7: Git Integration (Optional Dependency)

#### `git/LgGitService` (Project-level)
- Optional dependency on `Git4Idea` plugin
- Getting list of branches (local + remote) for target branch selector
- Getting current branch
- Graceful degradation if Git plugin is absent or project not under Git
- Methods: `isGitAvailable()`, `getBranches()`, `getCurrentBranch()`

**Dependencies:** Git4Idea plugin (optional dependency via `plugin.xml`)

**Consumers:** `LgCatalogService`, `LgControlPanel`

---

### Layer 8: Listeners (Reactivity)

#### `listeners/LgConfigFileListener` (Project-level)
- Listens for file changes in lg-cfg/ via `BulkFileListener`
- On change of sections.yaml, *.sec.yaml, *.ctx.md, *.tpl.md → catalog cache invalidation
- Notification to UI via `LgCatalogService` about need to refresh
- Debounce mechanism to avoid multiple reloads on batch changes

#### `listeners/LgSettingsChangeListener` (Application-level)
- Listens for changes in `LgSettingsService` via custom Topic
- On change of CLI path or Python interpreter → CLI resolver cache invalidation
- On change of tokenizer defaults → update UI defaults

**Implementation:** via IntelliJ Platform Message Bus

**Dependencies:** VFS API, Settings Services, Catalog Services

---

## Data Flows and Component Interaction

### Flow 1: Listing Generation

```
User Action (Generate Button Click)
  ↓
LgGenerateListingAction.actionPerformed()
  ↓
LgPanelStateService.getState() → CliParams
  ↓
LgListingGenerator.generate(params) [in coroutine with Progress]
  ↓
CliExecutor.execute(args) [Dispatchers.IO]
  ↓
CliResponseParser.parseText(stdout)
  ↓
LgVirtualFileService.openListing(content) [EDT]
  ↓
FileEditorManager.openFile() [platform opens in editor]
```

### Flow 2: Catalog Loading

```
Plugin Startup
  ↓
LgToolWindowFactory.createToolWindowContent()
  ↓
LgControlPanel init
  ↓
LgCatalogService.loadAll() [coroutine]
  ↓
Parallel coroutines:
  - CliExecutor.execute(["list", "sections"])
  - CliExecutor.execute(["list", "contexts"])
  - CliExecutor.execute(["list", "mode-sets"])
  - CliExecutor.execute(["list", "tag-sets"])
  - TokenizerCatalogService.getLibraries()
  - GitBranchCatalogService.getBranches()
  ↓
CliResponseParser.parse*() → Data models
  ↓
LgCatalogService._sections.value = result [StateFlow update]
  ↓
LgControlPanel collect() [Flow collector on EDT]
  ↓
UI Components update (ComboBox.removeAllItems/addItem)
```

### Flow 3: VFS Change → Reload

```
User edits lg-cfg/sections.yaml
  ↓
VFS fires VFileContentChangeEvent
  ↓
LgConfigFileListener.after(events)
  ↓
Filter: event.file?.parent?.name == "lg-cfg"?
  ↓
Debounce (500ms)
  ↓
LgCatalogService.reload() [coroutine]
  ↓
Reload flow (like in Flow 2)
```

### Flow 4: Send to AI

```
User Action (Send to AI Button)
  ↓
LgSendToAiAction.actionPerformed()
  ↓
Determine: context or section?
  ↓
LgGenerationService.generate*() [with Progress]
  ↓
content: String
  ↓
LgSettingsService.state.aiProvider → providerId
  ↓
AiIntegrationService.sendTo(providerId, content)
  ↓
Provider-specific logic:
  - Clipboard: CopyPasteManager.setContents()
  - OpenAI: HTTP request via OpenAI API
  - Cursor/Copilot: IDE command execution
  ↓
Success Notification or Error Notification
```

---

## State Management

### Three Levels of State

#### 1. Application State (`LgSettingsService`)
- **Lifetime:** entire IDE lifecycle
- **Scope:** all projects
- **Storage:** `~/.config/JetBrains/.../options/lg-settings.xml`
- **Sync:** roaming enabled
- **Content:** CLI path, Python interpreter, install strategy, default tokenizer/encoder/ctx-limit, AI provider, openAsEditable

#### 2. Project State (`LgPanelStateService`)
- **Lifetime:** while project is open
- **Scope:** current project
- **Storage:** `.idea/workspace.xml`
- **Sync:** disabled (not committed to VCS)
- **Content:** selected section, selected template, current modes, active tags, task text, target branch, tokenizer params (override defaults)

#### 3. Workspace State (`LgWorkspaceStateService`)
- **Lifetime:** while project is open
- **Scope:** current project
- **Storage:** `.idea/workspace.xml`
- **Sync:** disabled
- **Content:** UI-specific state (tree view mode, tool window tab selection, column widths, etc.)

### Reactive State Management

Catalog Services expose data via **Kotlin StateFlow**:

```
LgCatalogService
  ├── sections: StateFlow<List<String>>
  ├── contexts: StateFlow<List<String>>
  ├── modeSets: StateFlow<ModeSetsList>
  └── tagSets: StateFlow<TagSetsList>
```

UI subscribes via `scope.launch { flow.collect { ... } }` and updates on EDT.

---

## Threading Model

### Dispatchers Usage

- **Dispatchers.Default** — CPU-bound operations (JSON parsing, building data structures)
- **Dispatchers.IO** — CLI execution, file I/O
- **Dispatchers.EDT** — all UI updates, write actions

### Read/Write Actions

- **readAction { }** — reading PSI/VFS on background thread (if needed)
- **writeAction { }** — modifying VFS/PSI (only on EDT)

Within LG plugin **minimal** PSI usage — mostly working with file paths.

### Service Scopes

Project-level services receive injected `CoroutineScope`:

```
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,
    private val scope: CoroutineScope  // Auto-managed
)
```

Scope automatically cancels when project is disposed.

---

## Error Handling

### Typed Error Handling

Define sealed class for CLI results:

#### `models/CliResult`
```
sealed class CliResult<out T>
  - Success(data: T)
  - Failure(exitCode: Int, stderr: String)
  - Timeout(duration: Long)
  - NotFound(message: String)
```

Services return `CliResult<T>`, UI uses pattern-matching for handling.

### Error Notifications

- **CLI errors** → Error notification with "Show Details" action
- **Network errors** (AI providers) → Error notification with Retry and "Copy to Clipboard" fallback
- **Validation errors** → inline validation in UI (via UI DSL validation or `ValidationInfo`)

### Graceful Degradation

- Git unavailable → hide target branch selector
- CLI not found → show notification suggesting to configure in Settings
- AI provider unavailable → fallback to clipboard

---

## IntelliJ Platform Integration

### Extension Points (registration in plugin.xml)

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- Tool Window -->
    <toolWindow
        id="Listing Generator"
        anchor="right"
        factoryClass="...LgToolWindowFactory"
        icon="icons.LgIcons.ToolWindow"/>

    <!-- Settings -->
    <applicationConfigurable
        parentId="tools"
        instance="...LgSettingsConfigurable"/>

    <!-- Notification Groups -->
    <notificationGroup
        id="LG Notifications"
        displayType="BALLOON"/>
    <notificationGroup
        id="LG Important"
        displayType="STICKY_BALLOON"/>
</extensions>
```

### Actions Registration

```xml
<actions>
    <group id="LgMainGroup" text="Listing Generator" popup="true">
        <action id="LgGenerateListing" class="..."/>
        <action id="LgGenerateContext" class="..."/>
        <separator/>
        <action id="LgShowStats" class="..."/>
        <action id="LgShowIncluded" class="..."/>
        <separator/>
        <action id="LgCreateConfig" class="..."/>
        <action id="LgRunDoctor" class="..."/>

        <add-to-group group-id="ToolsMenu" anchor="last"/>
    </group>
</actions>
```

### Listeners Registration

```xml
<projectListeners>
    <listener
        topic="com.intellij.openapi.vfs.newvfs.BulkFileListener"
        class="...LgConfigFileListener"/>
</projectListeners>

<applicationListeners>
    <listener
        topic="...LgSettingsChangeListener.TOPIC"
        class="...LgSettingsChangeNotifier"/>
</applicationListeners>
```

### Optional Dependencies

```xml
<!-- Git integration (optional) -->
<depends optional="true" config-file="withGit.xml">
    Git4Idea
</depends>
```

`withGit.xml`:
```xml
<idea-plugin>
    <extensions defaultExtensionNs="com.intellij">
        <!-- Git-specific extensions if needed -->
    </extensions>
</idea-plugin>
```

---

## Models (Data Classes)

### CLI Response Models

Kotlin data classes for typed parsing of JSON responses from CLI:

#### `models/ReportSchema`
- Mapping JSON schema from CLI
- Properties: protocol, scope, target, tokenizerLib, encoder, ctxLimit, total, files, context
- Nested data classes: `TotalsData`, `FileRow`, `ContextBlock`

#### `models/DiagReport`
- Mapping of diagnostic report
- Properties: protocol, tool_version, root, config, cache, checks, env
- Nested: `DiagConfig`, `DiagCache`, `DiagCheck`, `DiagEnv`

#### `models/ModeSetsList`, `models/TagSetsList`
- Mapping for adaptive settings
- Hierarchical structure: ModeSet → Mode[], TagSet → Tag[]

### UI State Models

#### `models/UiState`
```
data class ControlPanelState(
    val selectedSection: String,
    val selectedTemplate: String,
    val tokenizerLib: String,
    val encoder: String,
    val ctxLimit: Int,
    val modes: Map<String, String>,
    val tags: Set<String>,
    val taskText: String,
    val targetBranch: String
)
```

Synchronized with `LgPanelStateService.State`.

---

## Dependency Injection and Service Location

IntelliJ Platform **does not use** a full-featured DI framework.

### Service Resolution

```
// Application service
service<LgSettingsService>()

// Project service
project.service<LgCatalogService>()
```

### Constructor Injection (Limited)

Only for Project/Module:

```
@Service(Service.Level.PROJECT)
class MyService(
    private val project: Project,      // ✅ Injected
    private val scope: CoroutineScope  // ✅ Injected (2024.1+)
)

// ❌ NOT SUPPORTED
class MyService(
    private val otherService: OtherService  // DOES NOT WORK
)
```

### Service Dependencies

Services get other services **on-demand** (not in constructor):

```
class LgGenerationService {
    fun generate() {
        val cliService = project.service<LgCliService>()  // Here
        cliService.execute(...)
    }
}
```

---

## Lifecycle Management

### Service Lifecycle

```
User opens project
  ↓
IntelliJ Platform creates Project instance
  ↓
First access to service<LgCatalogService>()
  ↓
Platform creates service instance
  ↓
Constructor executed
  ↓
CoroutineScope injected (if declared)
  ↓
Service ready
  ↓
... project usage ...
  ↓
User closes project
  ↓
Platform calls Disposable.dispose() (if implemented)
  ↓
CoroutineScope cancelled automatically
  ↓
Service destroyed
```

### UI Component Lifecycle

```
Tool Window opened
  ↓
LgToolWindowFactory.createToolWindowContent()
  ↓
Create LgControlPanel instance
  ↓
LgControlPanel init block:
  - Setup UI via Kotlin UI DSL
  - Subscribe to StateFlow from services
  - Launch initial data loading coroutines
  ↓
Tool Window displayed
  ↓
... user interactions ...
  ↓
Project closed or Tool Window disposed
  ↓
Disposable.dispose() on panel
  ↓
Coroutines cancelled
  ↓
Subscriptions removed
```

---

## Extensibility

### Adding a New AI Provider

1. Create class in `services/ai/providers/` implementing `AiProvider` interface
2. Register in `AiIntegrationService.providers` registry
3. Add to enum for Settings UI
4. No changes needed in Actions or UI

### Adding a New Action

1. Create class in `actions/` inheriting from `AnAction`
2. Register in `plugin.xml`
3. Optionally add to Action Group for toolbar
4. Action uses existing Services via service locator

### Adding a New Tab to Tool Window

1. Create panel class in `ui/toolwindow/`
2. Add to `LgToolWindowFactory.createToolWindowContent()`
3. Optionally create dedicated state service for new tab

### Adding a New Generation Type

1. Create dedicated generator service in `services/generation/`
2. Create Action for trigger
3. Add UI controls to Control Panel (if needed)
4. Use existing `CliExecutor` and `LgVirtualFileService`

---

## Plugin Configuration (plugin.xml Structure)

### Main Sections

```xml
<idea-plugin>
    <!-- Basic Information -->
    <id>lg.intellij</id>
    <name>Listing Generator</name>
    <version>1.0.0</version>
    <vendor email="..." url="...">Author</vendor>

    <!-- Compatibility -->
    <idea-version since-build="241" until-build="243.*"/>

    <!-- Dependencies -->
    <depends>com.intellij.modules.platform</depends>
    <depends optional="true" config-file="withGit.xml">
        Git4Idea
    </depends>

    <!-- Localization -->
    <resource-bundle>messages.LgBundle</resource-bundle>

    <!-- Extensions -->
    <extensions defaultExtensionNs="com.intellij">
        <!-- Services -->
        <!-- Tool Windows -->
        <!-- Settings -->
        <!-- Notification Groups -->
    </extensions>

    <!-- Actions -->
    <actions>
        <!-- Main Action Group -->
    </actions>

    <!-- Listeners -->
    <applicationListeners>
        <!-- ... -->
    </applicationListeners>
    <projectListeners>
        <!-- ... -->
    </projectListeners>
</idea-plugin>
```

---

## Testing Strategy

### Unit Tests
- **CLI Integration:** mock process handlers, verify argument construction
- **Services:** mock CLI responses, verify business logic
- **State Management:** verify persistence serialization/deserialization
- **Parsers:** verify JSON parsing for various CLI outputs

### Integration Tests
- **Tool Window:** verify content creation, tab management
- **Actions:** verify enablement conditions, context data access
- **Settings:** verify UI binding, apply/reset logic
- **VFS operations:** verify file creation, listener triggers

### UI Tests (Optional)
- RemoteRobot for automating UI interactions
- Critical user flows: generate listing, send to AI

---

## Security Considerations

### Sensitive Data

- **API Keys** (OpenAI) → storage via `PasswordSafe` API (not in `PersistentStateComponent`)
- **Task text** may contain sensitive info → do not log fully, only metadata

### CLI Execution

- **Path injection prevention:** validate CLI path before execution
- **Argument escaping:** proper escaping via `GeneralCommandLine.withParameters()`
- **Timeout enforcement:** all CLI calls with mandatory timeout
- **Stderr capturing:** for informative error messages without exposing sensitive paths

---

## Localization (i18n)

### Message Bundle

`resources/messages/LgBundle.properties`:
```properties
# Tool Window
toolwindow.stripe.title=LG
toolwindow.control.tab=Control Panel
toolwindow.included.tab=Included Files

# Actions
action.generate.listing.text=Generate Listing
action.generate.context.text=Generate Context
action.send.to.ai.text=Send to AI

# Notifications
notification.success.generated=Listing generated successfully
notification.error.cli.notfound=Listing Generator CLI not found

# Settings
settings.display.name=Listing Generator
settings.cli.path=CLI Path
```

Support for Russian localization via `LgBundle_ru.properties`.

### Localization in Code

```
LgBundle.message("action.generate.listing.text")
```

---

## Performance Optimizations

### Lazy Loading
- Services created on-demand on first access
- Catalog data loaded asynchronously when Tool Window opens
- Tree nodes for Included Files built lazily

### Caching
- CLI resolver caches resolved path
- Tokenizer catalog caches encoders list with TTL
- Catalog service caches sections/contexts until invalidation

### Batching
- VFS changes debounced (500ms) before reload
- Multiple CLI calls executed in parallel via `coroutineScope { }`

### Background Execution
- All CLI calls on `Dispatchers.IO`
- UI updates strictly on EDT via `withContext(Dispatchers.EDT)`
- Progress reporting via IntelliJ Platform Progress API

---

## Migration Path (VS Code → IntelliJ)

### Component Correspondence

| VS Code Extension | IntelliJ Plugin | Implementation |
|-------------------|-----------------|------------|
| Control Panel (webview) | `LgControlPanel` (Swing + UI DSL) | Tool Window tab |
| Included Files Tree (webview tree) | `LgIncludedFilesPanel` (Tree) | Tool Window tab |
| Stats Webview | `LgStatsDialog` (DialogWrapper) | Modal dialog |
| Doctor Webview | `LgDoctorDialog` (DialogWrapper) | Modal dialog |
| `media/ui/` components | `ui/components/` (Swing-based) | Native components |
| State persistence (VSCode API) | `PersistentStateComponent` | Platform API |
| Command registration | Actions + plugin.xml | Platform API |
| Settings (VSCode config) | `Configurable` + UI DSL | Platform API |

### Key Differences

1. **No Webviews:** Swing components and Kotlin UI DSL instead of HTML/CSS/JS
2. **No Custom Protocol:** `LightVirtualFile` instead of `lg://` URI scheme
3. **Platform Threading:** Kotlin Coroutines with platform-aware dispatchers instead of manual promise management
4. **Platform State:** `PersistentStateComponent` instead of VS Code state API

---

## Dependencies (Gradle)

### Main

```kotlin
dependencies {
    intellijPlatform {
        // Base platform
        intellijIdeaCommunity("2024.1")

        // Optional plugins
        bundledPlugin("Git4Idea")

        // Tools
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
        instrumentationTools()
    }

    // Kotlin coroutines (bundled in platform)
    // JSON parsing (kotlinx.serialization or jackson)
}
```

### Optional Dependencies

- **Git4Idea** — for Git integration (optional)
- No external HTTP clients (Java 11+ `HttpClient` used for OpenAI API)