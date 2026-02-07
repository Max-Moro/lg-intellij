<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Listing Generator Changelog

## [Unreleased]

### Added
- OpenAI Codex CLI integration with session-based execution and reasoning effort configuration
- **AI provider selector in Control Panel**
  - Provider selection moved from application settings to Control Panel
  - Automatic detection of available providers at startup
- **"Update AI Modes Template" action** for automatic generation of `ai-interaction.sec.yaml`
  - Supports custom modes and provider launch arguments
- **"Reset UI to Defaults" action** to reset all Control Panel settings
- **Context-dependent storage of modes and tags** — settings are saved separately for each context + provider combination
- Loading overlay for Control Panel during long operations

### Changed
- Redesigned AI Contexts interface: context → task description → provider and action buttons
- "agent" mode is now selected by default for ai-interaction mode-set
- Updated default encoder values for tokenization libraries with auto-selection on library change
- Improved UX with automatic refresh of dependent dropdowns (sections, modes, tags, branches)
- Internal architecture refactored: migrated to command-driven PCE state engine for improved stability and extensibility

### Fixed
- Provider availability filtering in Control Panel
- Combobox initialization and state validation

### Removed
- "Open Config" action — obsolete for modern lg-cfg/ structures

## [0.10.1] - 2026-01-15

### Fixed
- CLI version check on Linux GUI apps now uses ExecutableDetector (fixes repeated install attempts on Ubuntu)

## [0.10.0] - 2026-01-15

### Fixed
- CLI path resolution on Linux GUI apps (launched without shell PATH)
- Claude Code integration path encoding

### Changed
- Compatible with CLI ^0.10.0 — see [CLI changelog](https://github.com/Max-Moro/lg-cli/blob/main/CHANGELOG.md)

## [0.9.0] - 2025-11-25

### Added
- Control Panel with section/context selection
- Adaptive settings (modes, tags, task text)
- Included Files panel with tree view
- Statistics dialog with detailed metrics and charts
- AI integration (clipboard, JetBrains AI, GitHub Copilot, Junie)
- Git branch selection for code review mode
- Doctor diagnostics dialog with bundle creation
- Managed CLI installation via venv
- Settings page for CLI configuration