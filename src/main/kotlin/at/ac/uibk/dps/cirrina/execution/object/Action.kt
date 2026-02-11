package at.ac.uibk.dps.cirrina.execution.`object`

import at.ac.uibk.dps.cirrina.csm.Csml.*
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph

class ActionGraph private constructor() :
  SimpleDirectedGraph<Action, DefaultEdge>(DefaultEdge::class.java) {

  inline fun <reified T : Action> getActionsOfType(): List<T> = vertexSet().filterIsInstance<T>()

  companion object {
    fun create(actions: List<Action>, baseGraph: ActionGraph = ActionGraph()): ActionGraph =
      baseGraph.apply {
        var lastVertex = vertexSet().lastOrNull()

        actions.forEach { action ->
          addVertex(action)
          lastVertex?.let { addEdge(it, action) }
          lastVertex = action
        }
      }
  }
}

sealed interface Action {
  companion object {
    fun create(description: ActionDescription, name: String? = null): Action =
      when (description) {
        is EvalDescription -> EvalAction(Expression.create(description.expression))

        is InvokeDescription ->
          InvokeAction(
            description.type,
            description.mode,
            buildVariables(description.input),
            buildEvents(description.raises),
          )

        is MatchDescription ->
          MatchAction(
            description.cases.associate {
              Expression.create(it.of) to it.then.map { desc -> create(desc) }
            },
            description.default?.let { create(it) },
          )

        is RaiseDescription ->
          RaiseAction(
            Event.from(description.event),
            description.target?.let { Expression.create(it) },
          )

        is TimeoutDescription ->
          TimeoutAction(
            name ?: error("timeout action name required"),
            Expression.create(description.delay),
            create(description.`do`),
          )

        is ResetDescription -> TimeoutResetAction(description.name)

        is LogDescription -> LogAction(Expression.create(description.message))

        else -> error("unknown action type: ${description.javaClass.simpleName}")
      }

    private fun buildVariables(context: Map<String, String>) =
      context.map { (k, v) ->
        val expression = Expression.create(v)
        ContextVariable.lazy(k, expression)
      }

    private fun buildEvents(events: List<EventDescription>) = events.map { Event.from(it) }
  }
}

interface EventRaisingAction : Action {
  fun raises(): List<Event>
}

class EvalAction internal constructor(val expression: Expression) : Action {
  override fun toString() = "EvalAction(expression='$expression')"
}

class InvokeAction
internal constructor(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val raises: List<Event>,
) : EventRaisingAction {
  override fun raises(): List<Event> = raises

  override fun toString() =
    "InvokeAction(type='$type', mode='$mode', input='$input', raises='$raises')"
}

class MatchAction
internal constructor(val cases: Map<Expression, List<Action>>, val default: Action? = null) :
  EventRaisingAction {
  override fun raises(): List<Event> =
    (cases.values + listOfNotNull(default)).filterIsInstance<EventRaisingAction>().flatMap {
      it.raises()
    }

  override fun toString() = "MatchAction(cases='$cases', default='$default')"
}

class RaiseAction internal constructor(val event: Event, val target: Expression?) :
  EventRaisingAction {
  override fun raises(): List<Event> = listOf(event)

  override fun toString() = "RaiseAction(event='$event', target='$target')"
}

class TimeoutAction
internal constructor(val name: String, val delay: Expression, val `do`: Action) :
  EventRaisingAction {
  override fun raises(): List<Event> =
    (`do` as? RaiseAction)?.let { listOf(it.event) } ?: emptyList()

  override fun toString() = "TimeoutAction(name='$name', delay='$delay', do='${`do`}')"
}

class TimeoutResetAction internal constructor(val action: String) : Action {
  override fun toString() = "TimeoutResetAction(action='$action')"
}

class LogAction internal constructor(val message: Expression) : Action
