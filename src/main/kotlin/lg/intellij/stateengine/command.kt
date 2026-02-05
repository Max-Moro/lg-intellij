package lg.intellij.stateengine

/**
 * State Engine - Command and Rule Factories
 *
 * Provides factories for defining commands and rules with type safety.
 * Registry is isolated per instance, not global.
 *
 * Ported from VS Code Extension's state-engine/command.ts
 */

// ============================================
// Rule Registry
// ============================================

/**
 * Rule Registry - manages business rules for a specific state and result type.
 *
 * Each application creates its own registry instance.
 * Rules are registered via the rule() function bound to this registry.
 *
 * @param TState Application state type
 * @param TResult Rule result type (must extend RuleResult)
 */
class RuleRegistry<TState, TResult : RuleResult> {
    private val rules = mutableListOf<BusinessRule<TState, TResult>>()

    /**
     * Register a rule. Called by rule() factory.
     */
    fun register(rule: BusinessRule<TState, TResult>) {
        rules.add(rule)
    }

    /**
     * Get all registered rules.
     */
    fun getAll(): List<BusinessRule<TState, TResult>> {
        return rules.toList()
    }

    /**
     * Clear all rules (useful for testing).
     */
    fun clear() {
        rules.clear()
    }
}

// ============================================
// Command Implementation
// ============================================

/**
 * Implementation of BaseCommand with payload support.
 *
 * Stores payload in a type-safe manner while implementing BaseCommand.
 *
 * @param T Payload type
 * @property type Command type string (namespace/ACTION format)
 * @property payload Command payload data
 */
data class Command<T>(
    override val type: String,
    val payload: T
) : BaseCommand

/**
 * Command without payload (Unit as payload placeholder).
 */
typealias NoPayloadCommand = Command<Unit>

// ============================================
// Command Factory
// ============================================

/**
 * Builder for creating command definitions.
 *
 * Provides fluent API for defining commands with or without payload.
 *
 * @param type Command type string (namespace/ACTION format)
 */
class CommandBuilder(private val type: String) {

    /**
     * Create command definition with typed payload.
     *
     * @param TPayload Payload type
     * @return Command definition that creates commands with payload
     */
    fun <TPayload> payload(): CommandDef<TPayload> {
        val cmdType = this.type
        return object : CommandDef<TPayload> {
            override val type = cmdType

            override fun create(payload: TPayload): BaseCommand {
                return Command(type, payload)
            }
        }
    }

    /**
     * Create command definition without payload.
     *
     * @return Command definition that creates commands without payload
     */
    fun noPayload(): CommandDefNoPayload {
        return object : CommandDefNoPayload {
            override val type = this@CommandBuilder.type

            override fun create(): BaseCommand {
                return Command(type, Unit)
            }
        }
    }
}

/**
 * Create a command definition.
 *
 * Commands follow namespace/ACTION naming convention.
 *
 * @example
 * ```kotlin
 * val SelectContext = command("context/SELECT").payload<SelectContextPayload>()
 * val Initialize = command("lifecycle/INITIALIZE").noPayload()
 *
 * // Create command instances
 * val cmd1 = SelectContext.create(SelectContextPayload(template = "default"))
 * val cmd2 = Initialize.create()
 * ```
 *
 * @param type Command type string (namespace/ACTION format)
 * @return CommandBuilder for fluent API
 */
fun command(type: String): CommandBuilder {
    return CommandBuilder(type)
}

// ============================================
// Rule Factory
// ============================================

/**
 * Configuration for a business rule.
 *
 * @param TState Application state type
 * @param TPayload Command payload type
 * @param TResult Rule result type
 * @property condition Predicate to check if rule should apply
 * @property apply Function to compute rule result
 */
data class RuleConfig<TState, TPayload, TResult : RuleResult>(
    val condition: (state: TState, payload: TPayload) -> Boolean,
    val apply: (state: TState, payload: TPayload) -> TResult
)

/**
 * Configuration for a no-payload rule.
 *
 * @param TState Application state type
 * @param TResult Rule result type
 */
data class NoPayloadRuleConfig<TState, TResult : RuleResult>(
    val condition: (state: TState) -> Boolean,
    val apply: (state: TState) -> TResult
)

/**
 * Rule function interface for registering rules.
 *
 * Supports both payload and no-payload commands.
 *
 * @param TState Application state type
 * @param TResult Rule result type
 */
interface RuleFunction<TState, TResult : RuleResult> {

    /**
     * Register a rule for command with payload.
     *
     * @param TPayload Payload type
     * @param cmd Command definition
     * @param config Rule configuration with condition and apply
     */
    fun <TPayload> invoke(
        cmd: CommandDef<TPayload>,
        config: RuleConfig<TState, TPayload, TResult>
    )

    /**
     * Register a rule for command without payload.
     *
     * @param cmd Command definition without payload
     * @param config Rule configuration with condition and apply
     */
    fun invoke(
        cmd: CommandDefNoPayload,
        config: NoPayloadRuleConfig<TState, TResult>
    )
}

/**
 * Implementation of RuleFunction.
 */
private class RuleFunctionImpl<TState, TResult : RuleResult>(
    private val registry: RuleRegistry<TState, TResult>
) : RuleFunction<TState, TResult> {

    override fun <TPayload> invoke(
        cmd: CommandDef<TPayload>,
        config: RuleConfig<TState, TPayload, TResult>
    ) {
        registry.register(object : BusinessRule<TState, TResult> {
            override val trigger = cmd.type

            override fun condition(state: TState, command: BaseCommand): Boolean {
                @Suppress("UNCHECKED_CAST")
                val payload = (command as Command<TPayload>).payload
                return config.condition(state, payload)
            }

            override fun apply(state: TState, command: BaseCommand): TResult {
                @Suppress("UNCHECKED_CAST")
                val payload = (command as Command<TPayload>).payload
                return config.apply(state, payload)
            }
        })
    }

    override fun invoke(
        cmd: CommandDefNoPayload,
        config: NoPayloadRuleConfig<TState, TResult>
    ) {
        registry.register(object : BusinessRule<TState, TResult> {
            override val trigger = cmd.type

            override fun condition(state: TState, command: BaseCommand): Boolean {
                return config.condition(state)
            }

            override fun apply(state: TState, command: BaseCommand): TResult {
                return config.apply(state)
            }
        })
    }
}

/**
 * Create a rule factory bound to a specific registry.
 *
 * The returned function can register rules for both payload and no-payload commands.
 *
 * @example
 * ```kotlin
 * val registry = RuleRegistry<MyState, MyRuleResult>()
 * val rule = createRuleFactory(registry)
 *
 * // Rule with payload
 * rule(SelectContext, RuleConfig(
 *     condition = { state, payload -> payload.template != state.template },
 *     apply = { state, payload -> MyRuleResult(mutations = mapOf("template" to payload.template)) }
 * ))
 *
 * // Rule without payload
 * rule(Initialize, NoPayloadRuleConfig(
 *     condition = { state -> !state.initialized },
 *     apply = { state -> MyRuleResult(mutations = mapOf("initialized" to true)) }
 * ))
 * ```
 *
 * @param TState Application state type
 * @param TResult Rule result type
 * @param registry Rule registry to bind to
 * @return Rule function for registering rules
 */
fun <TState, TResult : RuleResult> createRuleFactory(
    registry: RuleRegistry<TState, TResult>
): RuleFunction<TState, TResult> {
    return RuleFunctionImpl(registry)
}

// ============================================
// Utility Extensions
// ============================================

/**
 * Extract payload from command.
 *
 * @param TPayload Expected payload type
 * @return Payload if command matches expected type, null otherwise
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified TPayload> BaseCommand.payloadOrNull(): TPayload? {
    return (this as? Command<*>)?.payload as? TPayload
}

/**
 * Extract payload from command, throwing if not found.
 *
 * @param TPayload Expected payload type
 * @return Payload
 * @throws IllegalStateException if command doesn't have expected payload type
 */
inline fun <reified TPayload> BaseCommand.payload(): TPayload {
    return payloadOrNull<TPayload>()
        ?: throw IllegalStateException(
            "Command $type does not have payload of type ${TPayload::class.simpleName}"
        )
}
