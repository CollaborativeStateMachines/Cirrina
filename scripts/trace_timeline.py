"""Create an interactive wall-clock timeline from a GraphML execution trace."""

import argparse
import json
import sys
from collections.abc import Mapping
from pathlib import Path
from typing import Any
from xml.etree.ElementTree import ParseError

import networkx as nx

TEMPLATE = Path(__file__).with_name("templates") / "trace_timeline.html"


def event_label(record: Mapping[str, Any]) -> str:
    event = record.get("event", record.get("topic", "?"))
    match record.get("type"):
        case "EVENT_EMITTED":
            return f"send {event} · {record.get('state', '?')}"
        case "EVENT_HANDLED":
            before = record.get("stateBefore", "?")
            after = record.get("stateAfter", "?")
            state = before if before == after else f"{before} → {after}"
            return f"handle {event} · {state}"
        case "STATE_ENTERED":
            return f"enter {record.get('state', '?')}"
        case "INSTANCE_CREATED":
            return "created"
        case _:
            return str(record.get("type", "observation"))


def instance_key(record: Mapping[str, Any]) -> tuple[str, str]:
    return (
        str(record.get("runtimeId", "")),
        str(record.get("instanceId") or record.get("instance", "unknown")),
    )


def timeline_data(graph: nx.MultiDiGraph) -> dict[str, Any]:
    """Use recorded epoch milliseconds directly, without causal reordering."""
    if not graph or not graph.is_directed():
        raise ValueError("the trace must be a nonempty directed graph")

    records = []
    skipped = 0
    for node, record in graph.nodes(data=True):
        timestamp = record.get("timestamp")
        if timestamp is None:
            skipped += 1
            continue
        # GraphML long values become Python integers. Keep them exact in JavaScript too,
        # and within the range supported by the browser's UTC date formatter.
        if type(timestamp) is not int or abs(timestamp) > 8_640_000_000_000_000:
            raise ValueError(
                f"record {node!r} needs an integer epoch-millisecond timestamp"
            )
        records.append(
            {
                "id": str(node),
                "timestamp": timestamp,
                "type": record.get("type", ""),
                "label": event_label(record),
                "attributes": dict(record),
            }
        )
    if not records:
        raise ValueError("the trace has no timestamped observations")

    records.sort(key=lambda record: (record["timestamp"], record["id"]))
    start, end = records[0]["timestamp"], records[-1]["timestamp"]
    if end - start > 2**53 - 1:
        raise ValueError(
            "the trace spans too much time for exact millisecond differences"
        )

    instances = {
        instance_key(record["attributes"]): record["attributes"] for record in records
    }
    keys = sorted(
        instances,
        key=lambda key: (
            str(instances[key].get("stateMachine", "")),
            str(instances[key].get("instance", "")),
            key,
        ),
    )
    lanes = [
        {
            "label": f"{instances[key].get('stateMachine', '?')} / {instances[key].get('instance', '?')}",
            "runtimeId": key[0],
            "instanceId": key[1],
        }
        for key in keys
    ]
    lane_numbers = {key: index for index, key in enumerate(keys)}
    for record in records:
        record["lane"] = lane_numbers[instance_key(record["attributes"])]

    # Keep the complete topology, including untimed boundary nodes. Path selection must
    # follow local order and causes as well as deliveries, even through hidden records.
    links = [
        {
            "source": str(source),
            "target": str(target),
            "kind": edge.get("kind", "unknown"),
        }
        for source, target, edge in graph.edges(data=True)
    ]
    return {
        "records": records,
        "lanes": lanes,
        "links": links,
        "start": start,
        "end": end,
        "untimed": skipped,
        "truncated": bool(graph.graph.get("truncated", False)),
    }


def render_timeline(graph: nx.MultiDiGraph, output: Path) -> None:
    data = json.dumps(timeline_data(graph), ensure_ascii=False, allow_nan=False)
    # Trace labels are data, including strings such as </script>. Never let them close
    # the JSON script element or become executable markup in the standalone HTML.
    for character in "<>&\u2028\u2029":
        data = data.replace(character, f"\\u{ord(character):04x}")
    page = TEMPLATE.read_text(encoding="utf-8").replace("<!--TRACE_DATA-->", data)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(page, encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="GraphML file from trace_exporter.py")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output HTML file (default: INPUT.timeline.html)",
    )
    args = parser.parse_args(argv)
    output = args.output or args.input.with_suffix(".timeline.html")
    if output.suffix.lower() != ".html":
        parser.error("--output must end in .html")
    if output.resolve() == args.input.resolve():
        parser.error("--output must differ from the input file")

    try:
        graph = nx.read_graphml(args.input, force_multigraph=True)
        render_timeline(graph, output)
    except (OSError, ValueError, ParseError, nx.NetworkXException) as error:
        print(f"Cannot visualize trace: {error}", file=sys.stderr)
        return 1
    print(
        f"Open {output.resolve()} in a browser; select X and Y to compare timestamps."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
