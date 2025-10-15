# Listing Generator — IntelliJ Platform Plugin

<!-- Plugin description -->
IntelliJ Platform integration for the **Listing Generator CLI** tool.

Provides interactive UI for generating dense code contexts optimized for AI assistants like ChatGPT, Copilot, Claude, and Gemini.

**Features:**
- Visual configuration of sections and contexts from `lg-cfg/`
- Adaptive settings: modes, tags, and filters
- Live statistics with token estimation
- Direct integration with AI providers
- Git branch comparison support

Visit [Listing Generator Documentation](https://github.com/Max-Moro/lg-cli) for more information.
<!-- Plugin description end -->

## Development Status

**Current Phase:** 4 ✅ Complete  
**Next Phase:** 5 (Catalog Services)

### Completed Phases

- ✅ **Phase 0:** Project personalization and first run
- ✅ **Phase 1:** CLI Integration Foundation
- ✅ **Phase 2:** Settings Infrastructure
- ✅ **Phase 3:** Tool Window Structure
- ✅ **Phase 4:** Control Panel UI (full layout)

### Phase 4 Highlights

Control Panel fully implemented with:
- 5 groups of controls (AI Contexts, Adaptive Settings, Inspect, Tokenization, Utilities)
- All UI elements functional with stub notifications
- State persistence via `LgPanelStateService`
- Conditional visibility (target branch when mode == "review")
- Mock data in selectors (to be replaced in Phase 5)

See [lg-cfg/log/PHASE-4-SUMMARY.md](lg-cfg/log/PHASE-4-COMPLETED.md) for details.
