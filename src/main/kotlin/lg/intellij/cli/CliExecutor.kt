package lg.intellij.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.models.CliResult
import java.nio.charset.StandardCharsets

/**
 * Executes Listing Generator CLI commands.
 * 
 * Features:
 * - Async execution via Kotlin Coroutines (suspend functions)
 * - Timeout support (default 120 seconds)
 * - UTF-8 encoding enforcement
 * - stdin support for task text
 * - Mock mode for development without real CLI
 * - Typed results via CliResult sealed class
 * 
 * Thread Safety: All operations are performed on Dispatchers.IO
 */
@Service(Service.Level.PROJECT)
class CliExecutor(private val project: Project) {
    
    private val log = logger<CliExecutor>()
    private val resolver = service<CliResolver>()

    /**
     * Default timeout for CLI execution in milliseconds.
     */
    var defaultTimeoutMs: Long = 120_000 // 2 minutes
    
    /**
     * Executes CLI command and returns structured result.
     * 
     * @param args Command arguments (without executable name)
     * @param stdinData Optional data to send to stdin (for --task -)
     * @param timeoutMs Timeout in milliseconds (defaults to [defaultTimeoutMs])
     * @param workingDirectory Working directory (defaults to project base path)
     * @return Typed result of execution
     */
    suspend fun execute(
        args: List<String>,
        stdinData: String? = null,
        timeoutMs: Long = defaultTimeoutMs,
        workingDirectory: String? = null
    ): CliResult<String> = withContext(Dispatchers.IO) {
        try {
            // Resolve CLI run specification
            val runSpec = resolver.resolve()

            // Build command line
            val commandLine = buildCommandLine(
                runSpec,
                args,
                workingDirectory ?: project.basePath
            )

            log.debug("Executing CLI: ${commandLine.commandLineString}")

            // Create process handler
            val handler = CapturingProcessHandler(commandLine)
            
            // Write stdin if provided
            if (stdinData != null) {
                log.debug("Writing ${stdinData.length} bytes to stdin")
                handler.processInput.use { stdin ->
                    stdin.write(stdinData.toByteArray(StandardCharsets.UTF_8))
                }
            }
            
            // Execute with timeout
            val output = handler.runProcess(timeoutMs.toInt())
            
            // Process result
            processOutput(output, commandLine.commandLineString)
            
        } catch (e: CliNotFoundException) {
            // Check if this is a silent error (subsequent failure after fatal error)
            if (e.silent) {
                CliResult.Unavailable(e.message ?: "CLI unavailable")
            } else {
                CliResult.NotFound(e.message ?: "CLI not found")
            }
        } catch (e: Exception) {
            log.error("CLI execution failed", e)
            CliResult.Failure(
                exitCode = -1,
                stderr = e.message ?: "Unknown error",
                stdout = ""
            )
        }
    }
    
    /**
     * Builds GeneralCommandLine with proper configuration.
     *
     * Uses CliRunSpec to construct command line with correct arguments.
     * Handles both direct CLI execution and Python module invocation.
     */
    private fun buildCommandLine(
        runSpec: CliRunSpec,
        args: List<String>,
        workingDirectory: String?
    ): GeneralCommandLine {
        // Combine run spec args with CLI command args
        // E.g., for Python: ["-m", "lg.cli"] + ["render", "sec:all"]
        val allArgs = runSpec.args + args

        val commandLine = GeneralCommandLine()
            .withExePath(runSpec.cmd)
            .withParameters(allArgs)
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment(mapOf(
                "PYTHONIOENCODING" to "utf-8",
                "PYTHONUTF8" to "1",
                "PYTHONUNBUFFERED" to "1"
            ))

        if (workingDirectory != null) {
            commandLine.withWorkDirectory(workingDirectory)
        }

        return commandLine
    }
    
    /**
     * Processes command output into typed result.
     */
    private fun processOutput(
        output: ProcessOutput,
        commandString: String
    ): CliResult<String> {
        return when {
            output.isTimeout -> {
                log.warn("CLI execution timeout: $commandString")
                CliResult.Timeout(defaultTimeoutMs)
            }
            
            output.exitCode != 0 -> {
                log.warn("CLI failed with exit code ${output.exitCode}: $commandString")

                // IMPORTANT: stderr contains Python traceback - always include in result
                if (output.stderr.isNotEmpty()) {
                    log.debug("Full stderr output:\n${output.stderr}")
                }
                
                CliResult.Failure(
                    exitCode = output.exitCode,
                    stderr = output.stderr,
                    stdout = output.stdout
                )
            }
            
            else -> {
                log.debug("CLI succeeded, output length: ${output.stdout.length}")

                // IMPORTANT: even on success stderr may contain useful information
                // (e.g., bundle path in `lg diag --bundle`)
                if (output.stderr.isNotEmpty()) {
                    log.debug("stderr on success:\n${output.stderr}")
                }
                
                CliResult.Success(
                    data = output.stdout,
                    stderr = output.stderr
                )
            }
        }
    }
}
