package lg.intellij.cli

/**
 * CLI Target address utilities.
 *
 * Handles scoped target names (e.g., "@scope:name") which require
 * a different prefix format: "sec@scope:name" instead of "sec:@scope:name".
 */
object CliTarget {

    /**
     * Build CLI target address from prefix and name.
     *
     * For regular names: "sec:name" or "ctx:name"
     * For scoped names (@scope:name): "sec@scope:name" or "ctx@scope:name"
     */
    fun build(prefix: String, name: String): String {
        return if (name.startsWith("@")) "$prefix$name" else "$prefix:$name"
    }

    /**
     * Check if a target address refers to a context (vs section).
     */
    fun isContext(target: String): Boolean {
        return target.startsWith("ctx:") || target.startsWith("ctx@")
    }

    /**
     * Extract the target name from a full target address.
     *
     * "sec:name" → "name"
     * "ctx:name" → "name"
     * "sec@scope:name" → "@scope:name"
     * "ctx@scope:name" → "@scope:name"
     */
    fun extractName(target: String): String {
        return target.replaceFirst(Regex("^(ctx|sec):?"), "")
    }
}
