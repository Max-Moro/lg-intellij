package lg.intellij.models

/**
 * Result of `lg init` command execution.
 * 
 * Maps JSON response from CLI:
 * ```json
 * {
 *   "ok": true,
 *   "preset": "basic",
 *   "target": "/path/to/lg-cfg",
 *   "created": ["sections.yaml", "..."],
 *   "conflicts": []
 * }
 * ```
 * 
 * Or error case:
 * ```json
 * {
 *   "ok": false,
 *   "preset": "basic",
 *   "conflicts": ["sections.yaml", "..."],
 *   "message": "Use --force to overwrite existing files."
 * }
 * ```
 */
data class InitResult(
    /**
     * True if initialization succeeded without conflicts (or with --force).
     */
    val ok: Boolean,
    
    /**
     * Selected preset name.
     */
    val preset: String = "",
    
    /**
     * Path to created lg-cfg directory (when successful).
     */
    val target: String? = null,
    
    /**
     * List of created files (relative to lg-cfg/).
     */
    val created: List<String> = emptyList(),
    
    /**
     * List of conflicting files (when ok=false and conflicts exist).
     */
    val conflicts: List<String> = emptyList(),
    
    /**
     * Error message (when ok=false).
     */
    val error: String? = null,
    
    /**
     * Additional message (e.g., "Use --force to overwrite").
     */
    val message: String? = null
)

