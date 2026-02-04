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
        is EvalDescription -> EvalAction(Expression.from(description.expression))

        is InvokeDescription ->
          InvokeAction(
            description.type,
            description.mode,
            buildVariables(description.input),
            buildEvents(description.raises),
          )

        is MatchDescription ->
          MatchAction(
            Expression.from(description.value),
            description.cases.associate { Expression.from(it.of) to create(it.then) },
            description.default?.let { create(it) },
          )

        is RaiseDescription ->
          RaiseAction(
            Event.from(description.event),
            description.target?.let { Expression.from(it) },
          )

        is TimeoutDescription ->
          TimeoutAction(
            name ?: error("timeout action name required"),
            Expression.from(description.delay),
            create(description.`do`),
          )

        is ResetDescription -> TimeoutResetAction(description.name)

        else -> error("unknown type: ${description.javaClass.simpleName}")
      }

    private fun buildVariables(context: Map<String, String>) =
      context.map { (k, v) ->
        val expression = Expression.from(v)
        ContextVariable.lazy(k, expression)
      }

    private fun buildEvents(events: List<EventDescription>) = events.map { Event.from(it) }
  }
}

interface EventRaisingAction : Action {
  fun raises(): List<Event>
}

class EvalAction internal constructor(val expression: Expression) : Action

class InvokeAction
internal constructor(
  val type: String,
  val mode: InvocationMode,
  val input: List<ContextVariable>,
  val raises: List<Event>,
) : EventRaisingAction {
  override fun raises(): List<Event> = raises
}

class MatchAction
internal constructor(
  val value: Expression,
  val cases: Map<Expression, Action>,
  val default: Action? = null,
) : EventRaisingAction {
  override fun raises(): List<Event> =
    (cases.values + listOfNotNull(default)).filterIsInstance<EventRaisingAction>().flatMap {
      it.raises()
    }
}

class RaiseAction internal constructor(val event: Event, val target: Expression?) :
  EventRaisingAction {
  override fun raises(): List<Event> = listOf(event)
}

class TimeoutAction
internal constructor(val name: String, val delay: Expression, val `do`: Action) :
  EventRaisingAction {
  override fun raises(): List<Event> =
    (`do` as? RaiseAction)?.let { listOf(it.event) } ?: emptyList()
}

class TimeoutResetAction internal constructor(val action: String) : Action
