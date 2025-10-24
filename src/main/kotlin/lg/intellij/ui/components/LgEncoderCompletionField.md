# LgEncoderCompletionField

Text field with auto-completion for encoder selection, based on IntelliJ Platform's `TextCompletionField`.

## Features

- **Auto-completion popup**: Shows suggestions from `TokenizerCatalogService`
- **Custom values support**: User can type any encoder name (not limited to suggestions)
- **Library-aware**: Automatically reloads suggestions when tokenizer library changes
- **Async loading**: Completion variants are loaded asynchronously without blocking UI
- **Smart filtering**: Filters suggestions as user types
- **Keyboard shortcuts**: Ctrl+Space to show all variants

## Architecture

### Base Component

Extends `TextCompletionField<TextCompletionInfo>` from IntelliJ Platform:
- Package: `com.intellij.openapi.externalSystem.service.ui.completion`
- Features: popup management, keyboard navigation, filtering

### Completion Collection

Uses `TextCompletionCollector.async()` with modification tracker:
- Invalidates cache when library changes
- Loads encoders via `TokenizerCatalogService.getEncoders(library, project)`
- Runs on IO dispatcher (non-blocking)

### Rendering

Custom `EncoderInfoRenderer` implements `TextCompletionRenderer`:
- Shows encoder name with matched text highlighting
- Supports optional icon (for future cached encoder indication)
- Supports optional description (for future encoder metadata)

## Usage

```kotlin
// Create field
val encoderField = LgEncoderCompletionField(project, parentDisposable)

// Set library (triggers suggestions reload)
encoderField.setLibrary("tiktoken")

// Set initial value
encoderField.text = "cl100k_base"

// Listen for changes (supports custom values)
encoderField.whenTextChangedFromUi(parentDisposable) { newText ->
    // Save to state
    stateService.state.encoder = newText
}
```

## Integration in Control Panel

In `LgControlPanel.createTokenizationSection()`:

```kotlin
encoderField = LgEncoderCompletionField(project, this).apply {
    setLibrary(stateService.getEffectiveTokenizerLib())
    text = stateService.getEffectiveEncoder()
    
    whenTextChangedFromUi(this@LgControlPanel) { newText ->
        stateService.state.encoder = newText
    }
}
```

When library changes:

```kotlin
libraryCombo.addActionListener {
    val newLib = selectedItem as? String
    if (newLib != null) {
        encoderField.setLibrary(newLib)  // Triggers reload
    }
}
```

## Lifecycle

- **Creation**: Component is created with project and parent disposable
- **Scope**: Internal coroutine scope for async loading (cancelled on dispose)
- **Disposal**: Registered with parent disposable, cancels async operations

## Future Enhancements

- Badge/icon for cached encoders (requires CLI support)
- Tooltip with encoder metadata (model type, context window, etc.)
- Grouping by encoder family (gpt-3.5, gpt-4, etc.)
- Recent encoders at top of suggestions

