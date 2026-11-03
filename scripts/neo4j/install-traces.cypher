CALL apoc.custom.installProcedure(
  'writeTraces(records :: LIST OF MAP) :: (written :: INTEGER)',
  "UNWIND $records AS record

  MERGE (runtime:TraceRuntime {id: record.runtimeId})
  MERGE (instance:TraceInstance {id: record.instanceId})
  SET instance.name = record.instance, instance.stateMachine = record.stateMachine
  MERGE (instance)-[:IN_RUNTIME]->(runtime)

  MERGE (n:TraceRecord {id: record.id})
  SET n += record
  MERGE (n)-[:IN_INSTANCE]->(instance)

  FOREACH (_ IN CASE WHEN record.type = 'INSTANCE_CREATED' THEN [1] ELSE [] END | SET n:InstanceCreated)
  FOREACH (_ IN CASE WHEN record.type = 'STATE_ENTERED' THEN [1] ELSE [] END | SET n:StateEntered)

  FOREACH (_ IN CASE WHEN record.causalParent IS NOT NULL THEN [1] ELSE [] END |
    MERGE (parent:TraceRecord {id: record.causalParent})
    MERGE (n)-[:CAUSED_BY]->(parent)
  )

  FOREACH (_ IN CASE WHEN record.previousRecord IS NOT NULL THEN [1] ELSE [] END |
    MERGE (previous:TraceRecord {id: record.previousRecord})
    MERGE (previous)-[:NEXT]->(n)
  )

  FOREACH (_ IN CASE WHEN record.type = 'EVENT_EMITTED' THEN [1] ELSE [] END |
    SET n:EventEmitted
    MERGE (event:TraceEvent {id: record.eventId})
    SET event.topic = record.event, event.channel = record.channel,
        event.source = record.instance, event.target = record.target
    MERGE (n)-[:EMITS]->(event)
  )

  FOREACH (_ IN CASE WHEN record.type = 'EVENT_HANDLED' THEN [1] ELSE [] END |
    SET n:EventHandled
    MERGE (event:TraceEvent {id: record.eventId})
    SET event.topic = record.event, event.channel = record.channel,
        event.source = record.source, event.target = record.target
    MERGE (n)-[:HANDLES]->(event)
  )

  RETURN count(*) AS written",
  'neo4j',
  'write'
);
