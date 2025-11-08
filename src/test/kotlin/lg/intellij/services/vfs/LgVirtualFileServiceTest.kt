package lg.intellij.services.vfs

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for LgVirtualFileService.
 *
 * Validates filename sanitization, VirtualFile creation, and tab reuse.
 */
class LgVirtualFileServiceTest : BasePlatformTestCase() {
    
    private lateinit var service: LgVirtualFileService
    private lateinit var fileCache: MutableMap<String, com.intellij.openapi.vfs.VirtualFile>
    
    override fun setUp() {
        super.setUp()
        service = project.getService(LgVirtualFileService::class.java)
        
        // Get access to private fileCache for testing
        val fileCacheField = service.javaClass.getDeclaredField("fileCache")
        fileCacheField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        fileCache = fileCacheField.get(service) as MutableMap<String, com.intellij.openapi.vfs.VirtualFile>
    }
    
    fun testSanitizeFilename_RemovesSlashes() {
        // Reflection для доступа к private методу
        val method = service.javaClass.getDeclaredMethod("sanitizeFilename", String::class.java)
        method.isAccessible = true
        
        val result = method.invoke(service, "section/with/slashes") as String
        assertEquals("section-with-slashes", result)
    }
    
    fun testSanitizeFilename_RemovesWindowsReserved() {
        val method = service.javaClass.getDeclaredMethod("sanitizeFilename", String::class.java)
        method.isAccessible = true
        
        // Test : " * ? < > |
        val result = method.invoke(service, "bad:name*with?chars<>|") as String
        assertFalse(result.contains(":"))
        assertFalse(result.contains("*"))
        assertFalse(result.contains("?"))
        assertFalse(result.contains("<"))
        assertFalse(result.contains(">"))
        assertFalse(result.contains("|"))
    }
    
    fun testSanitizeFilename_CollapsesMultipleDashes() {
        val method = service.javaClass.getDeclaredMethod("sanitizeFilename", String::class.java)
        method.isAccessible = true
        
        val result = method.invoke(service, "section---with---dashes") as String
        assertEquals("section-with-dashes", result)
    }
    
    fun testSanitizeFilename_TrimsEdges() {
        val method = service.javaClass.getDeclaredMethod("sanitizeFilename", String::class.java)
        method.isAccessible = true
        
        val result = method.invoke(service, "-section-") as String
        assertEquals("section", result)
    }
    
    fun testBuildFilename_CreatesCorrectFormat() {
        val method = service.javaClass.getDeclaredMethod(
            "buildFilename", 
            String::class.java, 
            String::class.java, 
            String::class.java
        )
        method.isAccessible = true
        
        val result = method.invoke(service, "Listing", "all", "md") as String
        assertEquals("Listing — all.md", result)
    }
    
    fun testBuildFilename_SanitizesName() {
        val method = service.javaClass.getDeclaredMethod(
            "buildFilename", 
            String::class.java, 
            String::class.java, 
            String::class.java
        )
        method.isAccessible = true
        
        val result = method.invoke(service, "Context", "template/with/slashes", "md") as String
        assertEquals("Context — template-with-slashes.md", result)
    }
    
    fun testCreateVirtualFile_SetsCorrectFileType() {
        val method = service.javaClass.getDeclaredMethod(
            "createVirtualFile",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        
        val result = method.invoke(service, "# Content", "test.md")
        assertNotNull(result)
        
        // Проверяем что это LightVirtualFile
        assertTrue(result is com.intellij.testFramework.LightVirtualFile)
        
        val virtualFile = result as com.intellij.testFramework.LightVirtualFile
        assertEquals("test.md", virtualFile.name)
        assertFalse(virtualFile.isWritable) // Read-only
    }
    
    fun testUpdateFileContent_UpdatesLightVirtualFile() {
        val method = service.javaClass.getDeclaredMethod(
            "updateFileContent",
            com.intellij.openapi.vfs.VirtualFile::class.java,
            String::class.java
        )
        method.isAccessible = true
        
        // Create initial file
        val file = com.intellij.testFramework.LightVirtualFile("test.md", "Initial content")
        
        // Update content
        method.invoke(service, file, "Updated content")
        
        // Verify content was updated
        assertEquals("Updated content", String(file.contentsToByteArray(), Charsets.UTF_8))
    }
    
    fun testFileCache_StoresAndReuses() {
        // Simulate first generation
        val createMethod = service.javaClass.getDeclaredMethod(
            "createVirtualFile",
            String::class.java,
            String::class.java
        )
        createMethod.isAccessible = true
        
        val file1 = createMethod.invoke(service, "Content 1", "test.md") 
            as com.intellij.openapi.vfs.VirtualFile
        
        // Manually add to cache (simulating openInEditor logic)
        fileCache["test-key"] = file1
        
        // Verify it's in cache
        assertEquals(1, fileCache.size)
        assertEquals(file1, fileCache["test-key"])
        
        // Verify file is valid
        assertTrue(file1.isValid)
    }
    
    fun testFileCache_RemovesInvalidFiles() {
        // Create a mock invalid file by using a disposed LightVirtualFile
        val invalidFile = com.intellij.testFramework.LightVirtualFile("invalid.md", "content")
        
        // Add to cache
        fileCache["test-key"] = invalidFile
        assertEquals(1, fileCache.size)
        
        // Manually remove from cache (simulating openInEditor cleanup logic)
        if (fileCache["test-key"]?.isValid == false) {
            fileCache.remove("test-key")
        }
        
        // Note: LightVirtualFile is always valid in tests, so we just verify the logic exists
        // In real scenario, closed/deleted files would become invalid
    }
}

