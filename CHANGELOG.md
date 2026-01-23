<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Listing Generator Changelog

## [Unreleased]

### Added
- OpenAI Codex CLI integration with session-based execution and reasoning effort configuration

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