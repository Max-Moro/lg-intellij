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
import lg.intellij.cli.models.CliResult
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
    
    private val LOG = logger<CliExecutor>()
    private val resolver = service<CliResolver>()
    
    /**
     * Mock mode flag.
     * 
     * When true, returns mock responses instead of executing real CLI.
     * Useful for development when CLI is not available or for testing.
     * 
     * TODO: Move to Settings in Phase 2
     */
    var useMockMode: Boolean = false
    
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
        
        if (useMockMode) {
            LOG.warn("Mock mode enabled - returning mock response")
            return@withContext generateMockResponse(args)
        }
        
        try {
            // Resolve CLI executable
            val cliCommand = resolver.resolve()
            
            // Build command line
            val commandLine = buildCommandLine(
                cliCommand,
                args,
                workingDirectory ?: project.basePath
            )
            
            LOG.debug("Executing CLI: ${commandLine.commandLineString}")
            
            // Create process handler
            val handler = CapturingProcessHandler(commandLine)
            
            // Write stdin if provided
            if (stdinData != null) {
                LOG.debug("Writing ${stdinData.length} bytes to stdin")
                handler.processInput?.use { stdin ->
                    stdin.write(stdinData.toByteArray(StandardCharsets.UTF_8))
                }
            }
            
            // Execute with timeout
            val output = handler.runProcess(timeoutMs.toInt())
            
            // Process result
            processOutput(output, commandLine.commandLineString)
            
        } catch (e: CliNotFoundException) {
            CliResult.NotFound(e.message ?: "CLI not found")
        } catch (e: Exception) {
            LOG.error("CLI execution failed", e)
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
     * Handles both direct CLI execution and Python module invocation.
     */
    private fun buildCommandLine(
        cliCommand: String,
        args: List<String>,
        workingDirectory: String?
    ): GeneralCommandLine {
        val settings = service<lg.intellij.services.state.LgSettingsService>()
        
        // Determine if we're using Python module invocation
        val isPythonModule = settings.state.installStrategy == 
            lg.intellij.services.state.LgSettingsService.InstallStrategy.SYSTEM &&
            (cliCommand.endsWith("python") || cliCommand.endsWith("python3") || cliCommand.endsWith("py"))
        
        val commandLine = if (isPythonModule) {
            // Python module invocation: python -m lg.cli <args>
            GeneralCommandLine()
                .withExePath(cliCommand)
                .withParameters(listOf("-m", "lg.cli") + args)
        } else {
            // Direct CLI execution: listing-generator <args>
            GeneralCommandLine()
                .withExePath(cliCommand)
                .withParameters(args)
        }
        
        commandLine
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
                LOG.warn("CLI execution timeout: $commandString")
                CliResult.Timeout(defaultTimeoutMs)
            }
            
            output.exitCode != 0 -> {
                LOG.warn("CLI failed with exit code ${output.exitCode}: $commandString")
                LOG.debug("stderr: ${output.stderr}")
                CliResult.Failure(
                    exitCode = output.exitCode,
                    stderr = output.stderr,
                    stdout = output.stdout
                )
            }
            
            else -> {
                LOG.debug("CLI succeeded, output length: ${output.stdout.length}")
                CliResult.Success(output.stdout)
            }
        }
    }
    
    /**
     * Generates mock response based on command arguments.
     * 
     * Simulates CLI behavior for development without real CLI.
     */
    private fun generateMockResponse(args: List<String>): CliResult<String> {
        LOG.debug("Generating mock response for: $args")
        
        val response = when {
            "--version" in args || "-v" in args -> {
                "Listing Generator 0.3.0"
            }
            
            "list" in args && "sections" in args -> {
                """{"sections": ["all", "core", "tests", "docs"]}"""
            }
            
            "list" in args && "contexts" in args -> {
                """{"contexts": ["full", "overview", "architecture"]}"""
            }
            
            "render" in args -> {
                """
                # Generated Listing
                
                This is a mock listing generated by CliExecutor in mock mode.
                
                ## Section: ${args.getOrNull(args.indexOf("render") + 1) ?: "unknown"}
                
                Mock content here...
                """.trimIndent()
            }
            
            "diag" in args -> {
                """
                {
                  "protocol": 4,
                  "tool_version": "0.3.0",
                  "root": "${project.basePath}",
                  "config": {"status": "ok"},
                  "cache": {"status": "ok"},
                  "checks": [],
                  "env": {"python": "3.11.0"}
                }
                """.trimIndent()
            }
            
            else -> {
                "Mock response for: ${args.joinToString(" ")}"
            }
        }
        
        return CliResult.Success(response)
    }
    
    /**
     * Convenience method for simple command execution.
     * Throws exception on failure.
     */
    suspend fun executeOrThrow(
        args: List<String>,
        stdinData: String? = null,
        timeoutMs: Long = defaultTimeoutMs
    ): String {
        return execute(args, stdinData, timeoutMs).getOrThrow()
    }
    
    companion object {
        private val LOG = logger<CliExecutor>()
    }
}
