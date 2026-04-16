package at.ac.uibk.dps.cirrina.spec

import at.ac.uibk.dps.cirrina.csm.Csml.ActionDescription
import at.ac.uibk.dps.cirrina.csm.Csml.CtrDescription
import at.ac.uibk.dps.cirrina.csm.Csml.EmitDescription
import at.ac.uibk.dps.cirrina.csm.Csml.EvalDescription
import at.ac.uibk.dps.cirrina.csm.Csml.InstantiateDescription
import at.ac.uibk.dps.cirrina.csm.Csml.InvokeDescription
import at.ac.uibk.dps.cirrina.csm.Csml.LogDescription
import at.ac.uibk.dps.cirrina.csm.Csml.MatchDescription
import at.ac.uibk.dps.cirrina.csm.Csml.ResetDescription
import at.ac.uibk.dps.cirrina.csm.Csml.TimeoutDescription
import at.ac.uibk.dps.cirrina.spec.Action.Companion.create
import kotlin.collections.component1
import kotlin.collections.component2

sealed interface Action {
  companion object {
    fun create(csml: Csml, description: ActionDescription, name: String? = null) =
      when (description) {
        is InvokeDescription -> Invoke(description)
        is EvalDescription -> Eval(description)
        is EmitDescription -> Emit(description)
        is TimeoutDescription ->
          Timeout(name ?: error("timeout action name required"), csml, description)
        is ResetDescription -> Reset(description.name)
        is MatchDescription -> Match(csml, description)
        is LogDescription -> Log(description)
        is InstantiateDescription -> Instantiate(csml, description)
        is CtrDescription -> Ctr(description)
        else -> error("unknown action type '${description.javaClass.simpleName}'")
      }
  }
}

interface EventRaisingAction : Action {
  fun raises(): List<Event>
}

class Invoke internal constructor(description: InvokeDescription) : EventRaisingAction {
  val type = description.type

  val mode = description.mode

  val input = description.input.map { (k, v) -> LazyContextVariable(k, Expression(v)) }

  val output = description.output

  val emits = description.emits.map { ConditionalEvent.from(it) }

  override fun raises(): List<Event> = emits.map { it.event }
}

class Eval internal constructor(description: EvalDescription) : Action {
  val expression = Expression(description.expression)
}

class Emit internal constructor(description: EmitDescription) : EventRaisingAction {
  val event = Event.from(description.event)

  val target: Expression? = description.target?.let { Expression(it) }

  override fun raises(): List<Event> = listOf(event)
}

class Timeout internal constructor(val name: String, csml: Csml, description: TimeoutDescription) :
  EventRaisingAction {
  val delay = Expression(description.delay)

  val triggers = create(csml, description.triggers)

  override fun raises(): List<Event> = (triggers as? Emit)?.let { listOf(it.event) } ?: emptyList()
}

class Reset internal constructor(val action: String) : Action {
  override fun toString() = "ResetAction(action='$action')"
}

class Match internal constructor(csml: Csml, description: MatchDescription) : EventRaisingAction {
  val cases =
    description.cases.associate {
      Expression(it.of) to it.yields.map { desc -> create(csml, desc) }
    }

  val default = description.default?.let { create(csml, it) }

  override fun raises(): List<Event> =
    (cases.values.flatten() + listOfNotNull(default))
      .filterIsInstance<EventRaisingAction>()
      .flatMap { it.raises() }
}

class Log internal constructor(description: LogDescription) : Action {
  val message = Expression(description.message)
}

class Instantiate internal constructor(csml: Csml, description: InstantiateDescription) : Action {
  // TODO: it.key is an expression?
  val instances = description.instances.map { Instance.create(csml, it.value, it.key).getOrThrow() }
}

class Ctr internal constructor(description: CtrDescription) : Action {
  val counter: String = description.counter

  val by = description.by

  val tags: Map<String, Expression>? = description.tags?.mapValues { (_, v) -> Expression(v) }
}
