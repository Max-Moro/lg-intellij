package lg.intellij.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Utilities for formatting numbers and sizes in Stats UI.
 */
object LgFormatUtils {
    
    private val numberFormat = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US))
    private val percentFormat = DecimalFormat("0.0", DecimalFormatSymbols(Locale.US))
    
    /**
     * Formats integer with thousand separators.
     * 
     * Example: 1234567 → "1,234,567"
     */
    fun formatInt(value: Long): String {
        return numberFormat.format(value)
    }
    
    /**
     * Formats percentage with one decimal place.
     * 
     * Example: 12.345 → "12.3%"
     */
    fun formatPercent(value: Double): String {
        return "${percentFormat.format(value)}%"
    }
    
    /**
     * Formats byte size in human-readable format.
     * 
     * Examples:
     * - 1024 → "1.0 KB"
     * - 1048576 → "1.0 MB"
     * - 500 → "500 B"
     */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = -1
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        val formatted = DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).format(size)
        return "$formatted ${units[unitIndex]}"
    }
}

