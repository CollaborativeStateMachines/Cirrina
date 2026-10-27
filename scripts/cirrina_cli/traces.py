import json
from collections import defaultdict
from collections.abc import Iterable, Mapping
from pathlib import Path
from typing import Any

import networkx as nx
from neo4j import Driver, Record


def query(
    driver: Driver, database: str, cypher: str, **parameters: Any
) -> list[Record]:
    return driver.execute_query(
        cypher, parameters_=parameters, database_=database, routing_="r"
    ).records


def latest_execution(driver: Driver, database: str) -> list[str]:
    latest = query(
        driver,
        database,
        """
        MATCH (record:TraceRecord) WHERE record.runtimeId IS NOT NULL
        RETURN record.runtimeId AS id
        ORDER BY record.timestamp DESC, record.id LIMIT 1
        """,
    )
    if not latest:
        return []

    runtimes = {latest[0]["id"]}

    frontier = runtimes.copy()
    while frontier:
        peers = query(
            driver,
            database,
            """
            MATCH (sender:EventEmitted)-[:EMITS]->(:TraceEvent)
                  <-[:HANDLES]-(receiver:EventHandled)
            WHERE sender.runtimeId IN $ids OR receiver.runtimeId IN $ids
            RETURN DISTINCT sender.runtimeId AS sender, receiver.runtimeId AS receiver
            """,
            ids=sorted(frontier),
        )
        connected = {
            row[key]
            for row in peers
            for key in ("sender", "receiver")
            if row[key] is not None
        }
        frontier = connected - runtimes

        runtimes.update(frontier)

    return sorted(runtimes)


def list_runtimes(driver: Driver, database: str) -> list[Record]:
    return query(
        driver,
        database,
        """
        MATCH (record:TraceRecord) WHERE record.runtimeId IS NOT NULL
        RETURN record.runtimeId AS id, collect(DISTINCT record.instance) AS instances,
               max(record.timestamp) AS lastSeen
        ORDER BY lastSeen DESC LIMIT 20
        """,
    )


def add_boundary(graph: nx.MultiDiGraph, node_id: str, **attributes: Any) -> None:
    if node_id not in graph:
        graph.add_node(node_id, type="UNOBSERVED", missing=True, **attributes)


def build_graph(records: Iterable[Mapping[str, Any]]) -> nx.MultiDiGraph:
    records = list(records)
    graph = nx.MultiDiGraph()
    emissions: dict[str, list[str]] = defaultdict(list)

    for record in records:
        attributes = {
            name: json.dumps(value) if isinstance(value, (list, dict)) else value
            for name, value in record.items()
            if value is not None
        }
        graph.add_node(record["id"], **attributes, missing=False)
        if record.get("type") == "EVENT_EMITTED":
            emissions[record["eventId"]].append(record["id"])

    for record in records:
        target = record["id"]

        for field, kind in (("causalParent", "cause"), ("previousRecord", "order")):
            source = record.get(field)

            if source:
                add_boundary(graph, source)
                graph.add_edge(source, target, key=kind, kind=kind)

        if record.get("type") != "EVENT_HANDLED":
            continue

        event_id = record["eventId"]
        event = {
            "eventId": event_id,
            "topic": record.get("event", ""),
            "channel": record.get("channel", ""),
        }

        senders = emissions.get(event_id, [])
        if not senders:
            source = f"event:{event_id}"
            add_boundary(graph, source, **event)
            senders = [source]

        for source in senders:
            graph.add_edge(
                source, target, key=f"event:{event_id}", kind="event", **event
            )

    return graph


def load_trace_graph(
    driver: Driver,
    database: str = "neo4j",
    runtimes: list[str] | None = None,
    limit: int = 200,
) -> nx.MultiDiGraph:
    if limit < 1:
        raise ValueError("limit must be positive")

    if runtimes is None:
        runtimes = latest_execution(driver, database)

    rows = query(
        driver,
        database,
        """
        MATCH (record:TraceRecord) WHERE record.runtimeId IN $runtimes
        RETURN properties(record) AS record
        ORDER BY record.timestamp DESC, record.id LIMIT $limit
        """,
        runtimes=runtimes,
        limit=limit + 1,
    )

    graph = build_graph(row["record"] for row in rows[:limit])

    graph.graph.update(truncated=len(rows) > limit, runtimeIds=json.dumps(runtimes))

    return graph


def export_graph(graph: nx.MultiDiGraph, path: Path) -> None:
    if path.suffix.lower() != ".graphml":
        raise ValueError("output must end in .graphml")

    path.parent.mkdir(parents=True, exist_ok=True)

    nx.write_graphml(graph, path)
