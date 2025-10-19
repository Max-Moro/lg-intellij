package lg.intellij.services.vfs

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import lg.intellij.services.state.LgSettingsService
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * Service for managing generated content display in editor.
 * 
 * Phase 8: Replacement for OutputPreviewDialog.
 * Creates VirtualFiles (in-memory or on disk) and opens them in editor.
 * 
 * Two modes (controlled by Settings):
 * 1. Virtual mode (default): LightVirtualFile in memory (read-only)
 * 2. Editable mode: temporary file on disk (editable)
 * 
 * Features:
 * - Tab reuse: repeated generations update existing tab instead of creating new ones
 * - Automatic cleanup: invalid files are removed from cache
 */
@Service(Service.Level.PROJECT)
class LgVirtualFileService(private val project: Project) {
    
    private val settings = LgSettingsService.getInstance()
    
    /**
     * Cache of VirtualFiles by key (type:name).
     * Used to reuse tabs for repeated generations.
     */
    private val fileCache = mutableMapOf<String, VirtualFile>()
    
    /**
     * Last known editable mode setting.
     * Used to detect setting changes and invalidate cache.
     */
    private var lastEditableMode: Boolean = settings.state.openAsEditable
    
    /**
     * Opens listing content in editor.
     * 
     * Reuses existing tab if same section was previously generated.
     * 
     * @param content Generated listing content
     * @param sectionName Section name for filename
     */
    fun openListing(content: String, sectionName: String) {
        val cacheKey = "listing:$sectionName"
        val filename = buildFilename("Listing", sectionName, "md")
        openInEditor(content, filename, cacheKey)
    }
    
    /**
     * Opens context content in editor.
     * 
     * Reuses existing tab if same template was previously generated.
     * 
     * @param content Generated context content
     * @param templateName Template name for filename
     */
    fun openContext(content: String, templateName: String) {
        val cacheKey = "context:$templateName"
        val filename = buildFilename("Context", templateName, "md")
        openInEditor(content, filename, cacheKey)
    }
    
    /**
     * Opens content in editor (internal logic).
     * 
     * Implements tab reuse logic:
     * 1. Check cache for existing valid file
     * 2. If editable file → update content and reopen
     * 3. If read-only file → close old tab and create new one
     * 4. If not found or invalid → create new file and cache it
     * 
     * @param cacheKey Unique key for caching (e.g., "listing:all", "context:default")
     */
    private fun openInEditor(content: String, filename: String, cacheKey: String) {
        try {
            // Check if editable mode setting changed
            if (settings.state.openAsEditable != lastEditableMode) {
                LOG.debug("Editable mode changed, clearing cache")
                fileCache.clear()
                lastEditableMode = settings.state.openAsEditable
            }
            
            val editorManager = FileEditorManager.getInstance(project)
            val cachedFile = fileCache[cacheKey]
            
            val virtualFile = if (cachedFile != null && cachedFile.isValid) {
                // Existing file found
                if (settings.state.openAsEditable && cachedFile !is LightVirtualFile) {
                    // Editable file — update content
                    LOG.debug("Updating existing editable file: $cacheKey")
                    updateFileContent(cachedFile, content)
                    cachedFile
                } else {
                    // Read-only file — close old, create new
                    LOG.debug("Replacing read-only file: $cacheKey")
                    editorManager.closeFile(cachedFile)
                    fileCache.remove(cacheKey)
                    
                    val newFile = createVirtualFile(content, filename)
                    fileCache[cacheKey] = newFile
                    newFile
                }
            } else {
                // No cached file or invalid
                if (cachedFile != null) {
                    fileCache.remove(cacheKey)
                    LOG.debug("Removed invalid file from cache: $cacheKey")
                }
                
                val newFile = if (settings.state.openAsEditable) {
                    createTempFile(content, filename)
                } else {
                    createVirtualFile(content, filename)
                }
                
                fileCache[cacheKey] = newFile
                LOG.debug("Created and cached new file: $cacheKey")
                newFile
            }
            
            // Open in editor with focus
            editorManager.openFile(virtualFile, true)
            LOG.debug("Opened file in editor: $filename (editable=${settings.state.openAsEditable})")
            
        } catch (e: Exception) {
            LOG.error("Failed to open content in editor", e)
            throw VirtualFileException("Failed to open content in editor: ${e.message}", e)
        }
    }
    
    /**
     * Updates content of existing VirtualFile.
     * 
     * Handles both LightVirtualFile (in-memory) and regular files (on disk).
     * Must be called from EDT (write action required for disk files).
     */
    private fun updateFileContent(file: VirtualFile, content: String) {
        when (file) {
            is LightVirtualFile -> {
                // Update in-memory content directly
                // LightVirtualFile allows mutation through setBinaryContent
                file.setBinaryContent(content.toByteArray(Charsets.UTF_8))
                LOG.debug("Updated LightVirtualFile content: ${file.name}")
            }
            else -> {
                // Update file on disk
                val path = file.toNioPath()
                path.writeText(content, Charsets.UTF_8)
                
                // Refresh VFS to pick up changes
                file.refresh(false, false)
                LOG.debug("Updated disk file content: ${file.path}")
            }
        }
    }
    
    /**
     * Creates LightVirtualFile (in-memory, read-only).
     */
    private fun createVirtualFile(content: String, filename: String): VirtualFile {
        // Determine FileType for syntax highlighting
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(filename)
        
        return LightVirtualFile(filename, fileType, content).apply {
            isWritable = false // Read-only
        }
    }
    
    /**
     * Creates temporary file on disk (editable).
     * 
     * Uses system temp directory with "lg-intellij" prefix.
     */
    private fun createTempFile(content: String, filename: String): VirtualFile {
        // Create temp directory
        val tempDir = Files.createTempDirectory("lg-intellij")
        val tempFilePath = tempDir.resolve(filename)
        
        // Write content
        tempFilePath.writeText(content, Charsets.UTF_8)
        
        // Convert to VirtualFile
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempFilePath)
            ?: throw VirtualFileException("Failed to find created temp file: $tempFilePath")
        
        LOG.debug("Created temp file: ${tempFilePath.toAbsolutePath()}")
        
        return virtualFile
    }
    
    /**
     * Builds safe filename from prefix, name and extension.
     * 
     * Sanitizes unsafe characters (/, \, :, ", *, ?, <, >, |) to prevent file system issues.
     * 
     * Example: "Listing — all.md"
     */
    private fun buildFilename(prefix: String, name: String, extension: String): String {
        val safeName = sanitizeFilename(name)
        return "$prefix — $safeName.$extension"
    }
    
    /**
     * Sanitizes filename by replacing unsafe characters.
     * 
     * Replaces:
     * - Path separators: / \
     * - Windows reserved: : " * ? < > |
     * - Control characters: 0x00-0x1F
     * 
     * Multiple consecutive unsafe chars are collapsed to single dash.
     */
    private fun sanitizeFilename(name: String): String {
        // Replace unsafe chars with dash
        val replaced = name.replace(UNSAFE_FILENAME_CHARS, "-")
        
        // Collapse multiple dashes/spaces
        val collapsed = replaced.replace(Regex("[-\\s]{2,}"), "-")
        
        return collapsed.trim('-', ' ')
    }
    
    companion object {
        private val LOG = logger<LgVirtualFileService>()
        
        /**
         * Regex pattern for unsafe filename characters.
         * Matches: / \ : " * ? < > | and control chars
         */
        private val UNSAFE_FILENAME_CHARS = Regex("[/\\\\:\"*?<>|\\u0000-\\u001F]+")
    }
}

/**
 * Exception thrown when VirtualFile operations fail.
 */
class VirtualFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

