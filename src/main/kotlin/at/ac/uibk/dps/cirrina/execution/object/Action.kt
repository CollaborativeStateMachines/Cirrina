package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.*
import at.ac.uibk.dps.cirrina.spec.ConditionalEvent
import at.ac.uibk.dps.cirrina.spec.ContextVariable
import at.ac.uibk.dps.cirrina.spec.Event
import at.ac.uibk.dps.cirrina.spec.Instance
import at.ac.uibk.dps.cirrina.spec.LazyContextVariable

sealed interface Action {
  companion object {
    fun create(description: ActionDescription, name: String? = null): Action =
      when (description) {
        is EvalDescription -> EvalAction(description.expression)

        is InvokeDescription ->
          InvokeAction(
            description.type,
            description.mode,
            buildVariables(description.input),
            description.output,
            buildConditionalEvents(description.emits),
          )

        is MatchDescription ->
          MatchAction(
            description.cases.associate { it.of to it.yields.map { desc -> create(desc) } },
            description.default?.let { create(it) },
          )

        is EmitDescription -> EmitAction(Event.from(description.event), description.target)

        is TimeoutDescription ->
          TimeoutAction(
            name ?: error("timeout action name required"),
            description.delay,
            create(description.triggers),
          )

        is ResetDescription -> TimeoutResetAction(description.name)

        is LogDescription -> LogAction(description.message)

        // is InstantiateDescription -> InstantiateAction(buildInstances(description.instances))

        is CtrDescription -> CtrAction(description.counter, description.by, description.tags)

        else -> error("unknown action type: ${description.javaClass.simpleName}")
      }

    private fun buildVariables(context: Map<String, String>) =
      context.map { (k, v) -> LazyContextVariable(k, v) }

    private fun buildConditionalEvents(events: List<ConditionalEventDescription>) =
      events.map { ConditionalEvent.from(it) }

    /*private fun buildInstances(instances: Map<String, InstanceDescription>) =
    instances.map { (k, v) -> Instance.create(v, null, k) }*/
  }
}

interface EventRaisingAction : Action {
  fun raises(): List<Event>
}

class EvalAction internal constructor(val expression: String) : Action {
  override fun toString() = "EvalAction(expression='$expression')"
}

class InvokeAction
internal constructor(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val output: List<String>,
  val emits: List<ConditionalEvent>,
) : EventRaisingAction {
  override fun raises(): List<Event> = emits.map { it.event }

  override fun toString() =
    "InvokeAction(type='$type', mode='$mode', input='$input', output='$output', emits='$emits')"
}

class MatchAction
internal constructor(val cases: Map<String, List<Action>>, val default: Action? = null) :
  EventRaisingAction {
  override fun raises(): List<Event> =
    (cases.values.flatten() + listOfNotNull(default))
      .filterIsInstance<EventRaisingAction>()
      .flatMap { it.raises() }

  override fun toString() = "MatchAction(cases='$cases', default='$default')"
}

class EmitAction internal constructor(val event: Event, val target: String?) : EventRaisingAction {
  override fun raises(): List<Event> = listOf(event)

  override fun toString() = "RaiseAction(event='$event', target='$target')"
}

class TimeoutAction
internal constructor(val name: String, val delay: String, val triggers: Action) :
  EventRaisingAction {
  override fun raises(): List<Event> =
    (triggers as? EmitAction)?.let { listOf(it.event) } ?: emptyList()

  override fun toString() = "TimeoutAction(name='$name', delay='$delay', do='${triggers}')"
}

class TimeoutResetAction internal constructor(val action: String) : Action {
  override fun toString() = "TimeoutResetAction(action='$action')"
}

class LogAction internal constructor(val message: String) : Action {
  override fun toString() = "LogAction(message='$message')"
}

class InstantiateAction internal constructor(val instances: Map<String, Instance>) : Action {
  override fun toString() = "InstantiateAction(instances='$instances')"
}

class CtrAction
internal constructor(val counter: String, val by: Long, val tag: Map<String, String>) : Action {
  override fun toString() = "CtrAction(metric='$counter', by='$by', tag='$tag')"
}
