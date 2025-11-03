package lg.intellij.ui.components

import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.*
import com.intellij.util.ui.UIUtil
import javax.swing.BorderFactory
import javax.swing.SwingUtilities

/**
 * Factory for creating task text input fields with proper theming and borders.
 * 
 * Uses EditorTextField for consistency with VCS commit message field and other IDE inputs.
 * Handles Dark theme border visibility issues.
 */
object LgTaskTextField {
    
    /**
     * Wrapper panel that contains EditorTextField with proper border handling.
     */
    class TaskFieldWrapper : javax.swing.JPanel {
        
        lateinit var editorField: EditorTextField
            private set
        
        // Use secondary constructor to avoid updateUI() issues
        constructor(field: EditorTextField) : super(java.awt.BorderLayout()) {
            editorField = field
            add(editorField, java.awt.BorderLayout.CENTER)
            isOpaque = false
            
            // Delay border update until editor is initialized
            SwingUtilities.invokeLater {
                updateBorder()
            }
        }
        
        override fun updateUI() {
            super.updateUI()
            // Only update border if editorField is initialized
            if (this::editorField.isInitialized) {
                updateBorder()
            }
        }
        
        private fun updateBorder() {
            if (!this::editorField.isInitialized) return

            val editor = editorField.editor ?: return
            if (editor is EditorEx) {
                @Suppress("UsePropertyAccessSyntax") // setBackgroundColor(null) cannot use property syntax
                editor.setBackgroundColor(null)
                editor.colorsScheme = getEditorColorScheme(editor)
                editorField.border = DarculaEditorTextFieldBorder(editorField, editor)
            }
        }
    }
    
    /**
     * Creates task text field with proper sizing and customizations.
     * 
     * @param project Project context
     * @param initialText Initial text content
     * @param placeholder Placeholder text (optional)
     * @param rows Preferred height in rows (default 3)
     * @param columns Preferred width in columns (default 60)
     * @return Wrapper panel containing configured EditorTextField
     */
    fun create(
        project: Project,
        initialText: String = "",
        placeholder: String? = null,
        rows: Int = 3,
        columns: Int = 60
    ): TaskFieldWrapper {
        // Create editor field with customizations
        val editorField = EditorTextFieldProvider.getInstance()
            .getEditorField(
                PlainTextLanguage.INSTANCE,
                project,
                getCustomizations()
            )
        
        editorField.text = initialText
        editorField.setFontInheritedFromLAF(false)
        
        // Set placeholder
        if (placeholder != null) {
            editorField.setPlaceholder(placeholder)
            editorField.setShowPlaceholderWhenFocused(true)
        }
        
        // Set preferred size
        val charWidth = editorField.getFontMetrics(editorField.font).charWidth('m')
        val lineHeight = editorField.getFontMetrics(editorField.font).height
        editorField.preferredSize = java.awt.Dimension(
            charWidth * columns,
            lineHeight * rows + 8 // +8 for padding
        )
        
        // Return wrapper with proper border handling
        return TaskFieldWrapper(editorField)
    }
    
    /**
     * Adds document change listener to field.
     */
    fun EditorTextField.addChangeListener(listener: (String) -> Unit) {
        addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                listener(text)
            }
        })
    }
    
    /**
     * Editor customizations for task text field.
     */
    private fun getCustomizations(): List<EditorCustomization> {
        return listOf(
            SoftWrapsEditorCustomization.ENABLED,
            AdditionalPageAtBottomEditorCustomization.DISABLED,
            EditorCustomization { editor ->
                @Suppress("UsePropertyAccessSyntax") // setBackgroundColor(null) cannot use property syntax
                editor.setBackgroundColor(null)
                editor.colorsScheme = getEditorColorScheme(editor)
                editor.settings.additionalLinesCount = 0
                editor.settings.isAdditionalPageAtBottom = false
                editor.contentComponent.border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
            }
        )
    }
    
    /**
     * Gets proper color scheme for editor based on current UI theme.
     */
    private fun getEditorColorScheme(editor: EditorEx): EditorColorsScheme {
        val isLaFDark = ColorUtil.isDark(UIUtil.getPanelBackground())
        val isEditorDark = EditorColorsManager.getInstance().isDarkEditor
        
        val colorsScheme = if (isLaFDark == isEditorDark) {
            EditorColorsManager.getInstance().globalScheme
        } else {
            EditorColorsManager.getInstance().schemeForCurrentUITheme
        }
        
        val wrappedScheme = editor.createBoundColorSchemeDelegate(colorsScheme)
        wrappedScheme.editorFontSize = UISettingsUtils.getInstance().scaledEditorFontSize.toInt()
        
        return wrappedScheme
    }
}

