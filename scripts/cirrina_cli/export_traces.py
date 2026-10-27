import json
from pathlib import Path
from typing import Annotated

import typer
from neo4j import GraphDatabase
from neo4j.exceptions import DriverError, Neo4jError
from rich.console import Console
from rich.table import Table
from rich.text import Text

from . import traces


def validate_output(path: Path) -> Path:
    if path.suffix.lower() != ".graphml":
        raise typer.BadParameter("output must end in .graphml")
    return path


def export_traces(
    uri: Annotated[
        str, typer.Option(envvar="NEO4J_URI", help="Neo4j connection URI.")
    ] = "bolt://localhost:7687",
    user: Annotated[
        str, typer.Option(envvar="NEO4J_USER", help="Neo4j user.")
    ] = "neo4j",
    password: Annotated[
        str,
        typer.Option(
            envvar="NEO4J_PASSWORD", show_default=False, help="Neo4j password."
        ),
    ] = "password",
    database: Annotated[
        str, typer.Option(envvar="NEO4J_DATABASE", help="Neo4j database.")
    ] = "neo4j",
    runtime: Annotated[
        list[str] | None,
        typer.Option(
            "--runtime", help="Runtime ID to include; repeat for multiple runtimes."
        ),
    ] = None,
    list_runtimes: Annotated[
        bool,
        typer.Option(
            "--list-runtimes", help="List recent runtime IDs and instance names."
        ),
    ] = False,
    limit: Annotated[
        int, typer.Option(min=1, help="Maximum observations to export.")
    ] = 200,
    output: Annotated[
        Path,
        typer.Option(
            "--output",
            "-o",
            dir_okay=False,
            callback=validate_output,
            help="Output .graphml file.",
        ),
    ] = Path("trace.graphml"),
) -> None:
    console = Console(markup=False, highlight=False)
    errors = Console(stderr=True, markup=False, highlight=False)

    try:
        with GraphDatabase.driver(
            uri,
            auth=(user, password),
            connection_timeout=5,
            connection_acquisition_timeout=10,
            max_transaction_retry_time=5,
        ) as driver:
            if list_runtimes:
                rows = traces.list_runtimes(driver, database)
                if not rows:
                    console.print("No trace runtimes found.")
                    return

                table = Table(
                    "Runtime", "Instances", box=None, header_style="bold cyan"
                )
                for row in rows:
                    table.add_row(
                        Text(row["id"]), Text(", ".join(sorted(row["instances"])))
                    )
                console.print(table)
                return

            graph = traces.load_trace_graph(driver, database, runtime, limit)

        if not graph:
            raise ValueError("No trace records found for the selected execution.")
        traces.export_graph(graph, output)
    except (Neo4jError, DriverError, OSError, ValueError) as error:
        errors.print(f"Cannot export trace: {error}", style="red")
        raise typer.Exit(code=1) from error

    console.print(
        f"Exported {graph.number_of_nodes()} nodes / {graph.number_of_edges()} edges "
        f"to {output.resolve()}",
        style="green",
        soft_wrap=True,
    )
    console.print(
        "Runtimes: " + ", ".join(json.loads(graph.graph["runtimeIds"])),
        soft_wrap=True,
    )
    if graph.graph["truncated"]:
        errors.print(
            "Partial export: increase --limit to include earlier observations.",
            style="yellow",
        )
