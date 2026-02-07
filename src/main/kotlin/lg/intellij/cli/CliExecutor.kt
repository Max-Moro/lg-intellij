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
import java.nio.charset.StandardCharsets

/**
 * Successful CLI output with both streams.
 */
data class CliOutput(val stdout: String, val stderr: String = "")

/**
 * Executes Listing Generator CLI commands.
 *
 * Returns [CliOutput] on success, throws [CliException] subclasses on error:
 * - [CliExecutionException] for non-zero exit codes
 * - [CliTimeoutException] for timeouts
 * - [CliNotFoundException] when CLI is not found
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
     * Executes CLI command.
     *
     * @param args Command arguments (without executable name)
     * @param stdinData Optional data to send to stdin (for --task -)
     * @param timeoutMs Timeout in milliseconds (defaults to [defaultTimeoutMs])
     * @param workingDirectory Working directory (defaults to project base path)
     * @return CLI output on success
     * @throws CliExecutionException if process exits with non-zero code
     * @throws CliTimeoutException if process exceeds timeout
     * @throws CliNotFoundException if CLI executable is not found
     */
    suspend fun execute(
        args: List<String>,
        stdinData: String? = null,
        timeoutMs: Long = defaultTimeoutMs,
        workingDirectory: String? = null
    ): CliOutput = withContext(Dispatchers.IO) {
        // Resolve CLI run specification (may throw CliNotFoundException)
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

        // Process result (throws on error)
        processOutput(output, commandLine.commandLineString, timeoutMs)
    }

    /**
     * Builds GeneralCommandLine with proper configuration.
     */
    private fun buildCommandLine(
        runSpec: CliRunSpec,
        args: List<String>,
        workingDirectory: String?
    ): GeneralCommandLine {
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
     * Processes command output. Returns [CliOutput] on success, throws on error.
     */
    private fun processOutput(
        output: ProcessOutput,
        commandString: String,
        timeoutMs: Long
    ): CliOutput {
        when {
            output.isTimeout -> {
                log.warn("CLI execution timeout: $commandString")
                throw CliTimeoutException(
                    "Process timeout after ${timeoutMs}ms",
                    timeoutMs
                )
            }

            output.exitCode != 0 -> {
                log.warn("CLI failed with exit code ${output.exitCode}: $commandString")
                if (output.stderr.isNotEmpty()) {
                    log.debug("Full stderr output:\n${output.stderr}")
                }
                throw CliExecutionException(
                    "CLI failed with exit code ${output.exitCode}",
                    output.exitCode,
                    output.stderr,
                    output.stdout
                )
            }

            else -> {
                log.debug("CLI succeeded, output length: ${output.stdout.length}")
                if (output.stderr.isNotEmpty()) {
                    log.debug("stderr on success:\n${output.stderr}")
                }
                return CliOutput(
                    stdout = output.stdout,
                    stderr = output.stderr
                )
            }
        }
    }
}
