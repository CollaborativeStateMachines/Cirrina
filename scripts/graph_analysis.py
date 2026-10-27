"""Schema-independent operations on attributed NetworkX graphs."""

import inspect
import json
import math
import operator
from collections import Counter
from collections.abc import Mapping
from itertools import islice
from statistics import fmean, median, pstdev, stdev
from typing import get_args

import networkx as nx

ALGORITHMS = {
    name: getattr(nx, name)
    for name in (
        "shortest_path",
        "shortest_path_length",
        "dag_longest_path",
        "dag_longest_path_length",
        "ancestors",
        "descendants",
        "connected_components",
        "strongly_connected_components",
        "weakly_connected_components",
        "topological_sort",
        "find_cycle",
        "simple_cycles",
        "degree_centrality",
        "betweenness_centrality",
        "clustering",
        "average_clustering",
        "is_directed_acyclic_graph",
        "minimum_spanning_tree",
        "transitive_reduction",
    )
}


def matches(attributes: dict, filters: dict) -> bool:
    """Match attribute predicates without evaluating user-provided code."""
    comparisons = {
        "eq": operator.eq,
        "ne": operator.ne,
        "gt": operator.gt,
        "ge": operator.ge,
        "lt": operator.lt,
        "le": operator.le,
        "in": lambda value, choices: value in choices,
    }
    for field, expected in filters.items():
        value = attributes.get(field)
        if isinstance(expected, dict):
            for name, operand in expected.items():
                if name == "exists":
                    accepted = (field in attributes) == operand
                elif name in comparisons:
                    accepted = field in attributes and comparisons[name](value, operand)
                else:
                    raise ValueError(f"unknown filter operator: {name}")
                if not accepted:
                    return False
        elif field not in attributes or (
            value not in expected if isinstance(expected, list) else value != expected
        ):
            return False
    return True


def numeric(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value)


def json_value(value):
    if isinstance(value, Mapping):
        return {str(k): json_value(v) for k, v in value.items()}
    if isinstance(value, (tuple, list, set)):
        return [json_value(v) for v in value]
    return value


def summarize(values: list, ddof: int) -> dict:
    """Use population/sample standard deviation and linearly interpolated quantiles."""
    ordered = sorted(values)

    def percentile(fraction):
        if not ordered:
            return None
        position = (len(ordered) - 1) * fraction
        lower, upper = ordered[math.floor(position)], ordered[math.ceil(position)]
        return lower + (upper - lower) * (position - math.floor(position))

    return {
        "count": len(values),
        "mean": fmean(values) if values else None,
        "median": median(values) if values else None,
        "stddev": (pstdev(values) if ddof == 0 else stdev(values))
        if len(values) > ddof
        else None,
        "p95": percentile(0.95),
        "p99": percentile(0.99),
        "min": min(values, default=None),
        "max": max(values, default=None),
        "sum": sum(values),
    }


class GraphAnalysis:
    """Keep full graphs and intermediate result tables outside the LLM context."""

    tool_names = (
        "describe",
        "select_nodes",
        "select_edges",
        "subgraph",
        "relation_projection",
        "between",
        "algorithm_info",
        "algorithm",
        "causal_intervals",
        "pair_intervals",
        "statistics",
        "repeated_paths",
        "endpoints",
        "rows",
    )

    def __init__(self, graph: nx.Graph):
        graph = graph.copy()
        for _, attributes in graph.nodes(data=True):
            for key, value in graph.graph.get("node_default", {}).items():
                attributes.setdefault(key, value)
        for *_, attributes in graph.edges(data=True):
            for key, value in graph.graph.get("edge_default", {}).items():
                attributes.setdefault(key, value)
        self.graphs = {"g0": graph}
        self.tables: dict[str, list[dict]] = {}
        self.kinds: dict[str, str] = {}

    def _store(self, rows: list[dict], kind: str = "rows", **details) -> dict:
        result = f"r{len(self.tables) + 1}"
        self.tables[result], self.kinds[result] = rows, kind
        return {"result": result, "count": len(rows), "sample": rows[:5], **details}

    def _ids(self, result: str) -> set:
        if self.kinds[result] != "nodes":
            raise ValueError("expected a node-selection result handle")
        return {row["id"] for row in self.tables[result]}

    @staticmethod
    def _edges(graph):
        if graph.is_multigraph():
            yield from graph.edges(keys=True, data=True)
        else:
            yield from ((a, b, None, data) for a, b, data in graph.edges(data=True))

    @staticmethod
    def _schema(records) -> dict:
        columns = {}
        for record in records:
            for key, value in record.items():
                columns.setdefault(key, []).append(value)
        return {
            key: {
                "types": sorted({type(v).__name__ for v in values}),
                "present": len(values),
                "distinct": len(set(map(json.dumps, values))),
                "examples": [
                    json.loads(v)
                    for v, _ in Counter(map(json.dumps, values)).most_common(8)
                ],
            }
            for key, values in columns.items()
        }

    def describe(self, graph: str = "g0") -> dict:
        """Discover graph type, metadata and node/edge attribute schemas with example values.
        The input graph is g0. Attribute meanings and units are not inferred by this tool.
        """
        data = self.graphs[graph]
        return {
            "graph": graph,
            "nodes": len(data),
            "edges": data.number_of_edges(),
            "directed": data.is_directed(),
            "multigraph": data.is_multigraph(),
            "metadata": data.graph,
            "node_attributes": self._schema(d for _, d in data.nodes(data=True)),
            "edge_attributes": self._schema(d for *_, d in data.edges(data=True)),
        }

    def select_nodes(self, filters: dict, graph: str = "g0") -> dict:
        """Select nodes using discovered attributes; return a table handle and preview.
        Predicates are ANDed. A scalar means equality, a list means membership, and a
        dictionary supports eq, ne, gt, ge, lt, le, in, exists. {} selects all nodes.
        Rows separate node id from its original attributes. Example: {"cost":{"gt":5}}.
        """
        return self._store(
            [
                {"id": n, "attributes": dict(d)}
                for n, d in self.graphs[graph].nodes(data=True)
                if matches(d, filters)
            ],
            "nodes",
        )

    def select_edges(self, filters: dict, graph: str = "g0") -> dict:
        """Select edges by attribute predicates (same syntax as select_nodes).
        Rows retain source, target, multigraph key, and original attributes separately.
        """
        return self._store(
            [
                {"source": a, "target": b, "key": k, "attributes": dict(d)}
                for a, b, k, d in self._edges(self.graphs[graph])
                if matches(d, filters)
            ],
            "edges",
        )

    def subgraph(
        self,
        nodes: str = "",
        edges: str = "",
        graph: str = "g0",
        undirected: bool = False,
        simple: bool = False,
    ) -> dict:
        """Create a graph handle filtered by node and/or edge selection handles.
        Omitted selections retain all nodes/edges. Nodes without retained edges remain.
        undirected explicitly discards direction. simple collapses parallel edges and
        drops edge attributes, so it is an UNWEIGHTED projection. The original is retained.
        """
        data = self.graphs[graph].copy()
        if nodes:
            data = data.subgraph(self._ids(nodes)).copy()
        if edges:
            if self.kinds[edges] != "edges":
                raise ValueError("expected an edge-selection result handle")
            selected = {
                (r["source"], r["target"], r["key"]) for r in self.tables[edges]
            }
            removed = [
                (a, b, k)
                for a, b, k, _ in self._edges(data)
                if (a, b, k) not in selected
                and (data.is_directed() or (b, a, k) not in selected)
            ]
            data.remove_edges_from(
                removed if data.is_multigraph() else [(a, b) for a, b, _ in removed]
            )
        if undirected:
            data = data.to_undirected()
        if simple:
            projection = nx.DiGraph() if data.is_directed() else nx.Graph()
            projection.graph.update(data.graph)
            projection.graph.pop("edge_default", None)
            projection.add_nodes_from(data.nodes(data=True))
            projection.add_edges_from(data.edges())
            data = projection
        result = f"g{len(self.graphs)}"
        self.graphs[result] = data
        return {"graph": result, "nodes": len(data), "edges": data.number_of_edges()}

    def relation_projection(
        self, direct: str, left: str, right: str, nodes: str = "", graph: str = "g0"
    ) -> dict:
        """Retain direct selected edges and join left/right edge selections by shared TARGET.
        Each left edge (a,m) and right edge (b,m) produces a directed edge (a,b), with
        joined_via=m as provenance. This can materialize relations through intermediate
        entity vertices without treating a shared target as an existing directed path.
        Optional nodes selects the output vertices. Direct edge attributes are retained;
        the original graph is unchanged. Report cycles rather than silently removing them.
        """
        data = self.graphs[graph]
        if not data.is_directed():
            raise ValueError("relation_projection requires a directed input graph")
        for selection in (direct, left, right):
            if self.kinds[selection] != "edges":
                raise ValueError("expected edge-selection handles")
        selected = self._ids(nodes) if nodes else set(data)
        projected = nx.MultiDiGraph()
        projected.graph.update(data.graph)
        projected.graph.pop("edge_default", None)
        projected.add_nodes_from((n, data.nodes[n]) for n in selected)
        for row in self.tables[direct]:
            if row["source"] in selected and row["target"] in selected:
                key = projected.add_edge(row["source"], row["target"])
                projected.edges[row["source"], row["target"], key].update(
                    row["attributes"]
                )
        receivers = {}
        for row in self.tables[right]:
            receivers.setdefault(row["target"], []).append(row["source"])
        for row in self.tables[left]:
            for target in receivers.get(row["target"], []):
                if row["source"] in selected and target in selected:
                    projected.add_edge(row["source"], target, joined_via=row["target"])
        result = f"g{len(self.graphs)}"
        self.graphs[result] = projected
        return {
            "graph": result,
            "nodes": len(projected),
            "edges": projected.number_of_edges(),
            "acyclic": nx.is_directed_acyclic_graph(projected),
        }

    def between(self, source: str, target: str, graph: str = "g0") -> dict:
        """Return the induced subgraph of ALL paths between two vertices in a DAG.
        Include both endpoints and every vertex reachable from source that can reach
        target. Preserve all internal edges and attributes. This retains every branch,
        unlike a shortest-path witness. The result is a new graph handle.
        """
        data = self.graphs[graph]
        if not data.is_directed() or not nx.is_directed_acyclic_graph(data):
            raise ValueError("between requires a directed acyclic graph projection")
        if not nx.has_path(data, source, target):
            raise nx.NetworkXNoPath("no directed path connects the endpoints")
        nodes = ({source} | nx.descendants(data, source)) & (
            {target} | nx.ancestors(data, target)
        )
        result = f"g{len(self.graphs)}"
        self.graphs[result] = data.subgraph(nodes).copy()
        return {
            "graph": result,
            "source": source,
            "target": target,
            "nodes": len(nodes),
            "edges": self.graphs[result].number_of_edges(),
        }

    def algorithm_info(self, name: str) -> dict:
        """Read a supported NetworkX algorithm's signature and documentation before use."""
        function = ALGORITHMS[name]
        return {
            "name": name,
            "signature": str(inspect.signature(function)),
            "documentation": inspect.getdoc(function)[:5000],
        }

    def algorithm(self, name: str, parameters: dict, graph: str = "g0") -> dict:
        """Execute a supported NetworkX algorithm with G supplied by its graph handle.
        Use algorithm_info for accepted parameters. Scalar results are returned directly;
        graph results get a new graph handle; other results get full paginated tables.
        Enumerations above 10000 rows fail explicitly: narrow the graph or bound the query.
        This tool never executes generated Python code.
        """
        value = ALGORITHMS[name](self.graphs[graph], **parameters)
        if isinstance(value, nx.Graph):
            result = f"g{len(self.graphs)}"
            self.graphs[result] = value
            return self.describe(result)
        if value is None or isinstance(value, (str, bool, int, float)):
            return {"value": value}
        rows = (
            ({"item": k, "value": json_value(v)} for k, v in value.items())
            if isinstance(value, Mapping)
            else ({"value": json_value(v)} for v in value)
        )
        rows = list(islice(rows, 10001))
        if len(rows) > 10000:
            raise ValueError(
                "algorithm exceeds 10000 result rows; narrow or bound the query"
            )
        return self._store(rows)

    def causal_intervals(
        self,
        starts: str,
        ends: str,
        time_attribute: str,
        group_by: list,
        graph: str = "g0",
        unit: str = "unspecified",
        end_group_by: list | None = None,
        include_subgraphs: bool = False,
    ) -> dict:
        """Trace each selected end backward to its nearest matching start in a causal DAG.
        The caller selects which graph edges represent causality. Traverse multiple hops
        and branches; an immediate predecessor need not be a matching start.
        Only starts with matching group_by attributes are eligible. Discard candidate
        starts that are ancestors of another candidate, leaving the causally latest ones,
        rather than choosing by timestamp or hop count. Incomparable candidates are
        ambiguous and excluded. One start may lead to several ends. Multiple routes
        between the same endpoints count ONCE; a witness path is returned as evidence.
        Time units and attribute names are caller-supplied. Invalid timestamps and
        unmatched observations are excluded, never estimated.
        end_group_by optionally maps different end attribute names onto the start's
        group_by keys by position. Do not require matching contexts when endpoints may
        cross contexts/nodes. include_subgraphs retains the induced all-branches interval
        graph for each pair, with its handle and vertex/edge counts in the result row.
        """
        data = self.graphs[graph]
        if not data.is_directed() or not nx.is_directed_acyclic_graph(data):
            raise ValueError(
                "causal_intervals requires a directed acyclic graph projection"
            )
        first, last = self._ids(starts), self._ids(ends)
        if first & last or not (first | last) <= set(data):
            raise ValueError("selections must be disjoint and contained in the graph")
        end_keys = group_by if end_group_by is None else end_group_by
        if len(group_by) != len(end_keys):
            raise ValueError("start and end grouping keys must have the same length")
        keys = {n: group_by if n in first else end_keys for n in first | last}
        if any(k not in data.nodes[n] for n in first | last for k in keys[n]):
            raise ValueError(
                "grouping attributes are missing; refine selections or grouping keys"
            )
        groups = {
            n: tuple(json.dumps(data.nodes[n][k], sort_keys=True) for k in keys[n])
            for n in first | last
        }
        rows, excluded, matched = [], [], set()
        for end in sorted(last, key=str):
            candidates = {
                n for n in nx.ancestors(data, end) & first if groups[n] == groups[end]
            }
            nearest = candidates.copy()
            for candidate in candidates:
                nearest.difference_update(nx.ancestors(data, candidate))
            if len(nearest) != 1:
                excluded.append(
                    {
                        "end_id": end,
                        "candidate_start_ids": sorted(nearest, key=str),
                        "reason": "ambiguous_causal_starts"
                        if nearest
                        else "no_matching_causal_start",
                    }
                )
                continue
            start = next(iter(nearest))
            begin, finish = (
                data.nodes[start].get(time_attribute),
                data.nodes[end].get(time_attribute),
            )
            if not numeric(begin) or not numeric(finish) or finish < begin:
                excluded.append(
                    {
                        "start_id": start,
                        "end_id": end,
                        "reason": "invalid_or_decreasing_time",
                    }
                )
                continue
            matched.add(start)
            row = {
                "start_id": start,
                "end_id": end,
                "start": begin,
                "end": finish,
                "elapsed": finish - begin,
                "group": {
                    k: data.nodes[end][v]
                    for k, v in zip(group_by, end_keys, strict=True)
                },
                "witness_path": nx.shortest_path(data, start, end),
            }
            if include_subgraphs:
                interval = self.between(start, end, graph)
                row.update(
                    subgraph=interval["graph"],
                    node_count=interval["nodes"],
                    edge_count=interval["edges"],
                )
            rows.append(row)
        excluded.extend(
            {"id": n, "reason": "start_without_matched_end"}
            for n in sorted(first - matched, key=str)
        )
        return {
            "intervals": self._store(rows, unit=unit),
            "excluded": self._store(excluded),
            "time_attribute": time_attribute,
            "group_by": group_by,
            "end_group_by": end_keys,
            "matching": "nearest matching ancestors by causal reachability",
            "graph": graph,
        }

    def pair_intervals(
        self,
        starts: str,
        ends: str,
        time_attribute: str,
        group_by: list,
        graph: str = "g0",
        pairing: str = "strict",
        require_path: bool = True,
        unit: str = "unspecified",
    ) -> dict:
        """Pair selected endpoints using EXPLICIT numeric time attribute and grouping keys.
        This is SEQUENTIAL queue pairing plus an optional reachability check. Use
        causal_intervals instead when each end must independently find its causal start.
        No time unit, grouping key, start/end meaning, or edge meaning is built in.
        group_by=[] puts every observation in one group. Include correlation/resource keys
        when needed. strict excludes overlapping starts; fifo/lifo explicitly resolve them.
        require_path also requires graph reachability. Directed DAGs use topological order;
        other graphs use numeric time order. Invalid, ambiguous or unmatched observations
        are excluded, never replaced with zero. Result rows retain both endpoint IDs.
        """
        if pairing not in {"strict", "fifo", "lifo"}:
            raise ValueError("pairing must be strict, fifo, or lifo")
        data = self.graphs[graph]
        first, last = self._ids(starts), self._ids(ends)
        selected = first | last
        if first & last or not selected <= set(data):
            raise ValueError(
                "selections must be disjoint and contained in the selected graph"
            )
        pending, rows, excluded = {}, [], []

        def order_key(node):
            value = data.nodes[node].get(time_attribute)
            return (value if numeric(value) else math.inf, str(node))

        topological = data.is_directed() and nx.is_directed_acyclic_graph(data)
        ordered = (
            nx.lexicographical_topological_sort(data, key=order_key)
            if topological
            else sorted(first | last, key=order_key)
        )
        for node in ordered:
            if node not in selected:
                continue
            attributes = data.nodes[node]
            if not numeric(attributes.get(time_attribute)) or any(
                k not in attributes for k in group_by
            ):
                excluded.append({"id": node, "reason": "missing_group_or_numeric_time"})
                continue
            key = tuple(json.dumps(attributes[k], sort_keys=True) for k in group_by)
            queue = pending.setdefault(key, [])
            if node in first:
                queue.append(node)
                continue
            if not queue:
                excluded.append({"id": node, "reason": "end_without_start"})
                continue
            if len(queue) > 1 and pairing == "strict":
                excluded.append(
                    {
                        "start_ids": queue[:],
                        "end_id": node,
                        "reason": "ambiguous_pairing",
                    }
                )
                queue.clear()
                continue
            start = queue.pop(-1 if pairing == "lifo" else 0)
            begin, end = data.nodes[start][time_attribute], attributes[time_attribute]
            if end < begin or (require_path and not nx.has_path(data, start, node)):
                excluded.append(
                    {
                        "start_id": start,
                        "end_id": node,
                        "reason": "decreasing_time_or_no_path",
                    }
                )
                continue
            rows.append(
                {
                    "start_id": start,
                    "end_id": node,
                    "start": begin,
                    "end": end,
                    "elapsed": end - begin,
                    "group": {k: attributes[k] for k in group_by},
                }
            )
        excluded.extend(
            {"id": n, "reason": "start_without_end"}
            for queue in pending.values()
            for n in queue
        )
        return {
            "intervals": self._store(rows, unit=unit),
            "excluded": self._store(excluded),
            "time_attribute": time_attribute,
            "group_by": group_by,
            "pairing": pairing,
            "require_path": require_path,
            "ordering": "topological" if topological else "numeric_time",
        }

    def _column(self, result: str, row: dict, field: str):
        if self.kinds[result] in {"nodes", "edges"}:
            return row["attributes"][field]
        return row[field] if field in row else row["group"][field]

    def statistics(
        self, result: str, field: str, group_by: str = "", ddof: int = 0
    ) -> dict:
        """Compute count, mean, median, stddev, p95, p99, min, max, sum on FULL results.
        For node/edge selections, field names refer to original attributes. For intervals,
        use elapsed. group_by optionally names an attribute or an interval grouping key.
        Means for empty tables are null. No calculations use the five-row preview.
        ddof=0 uses population standard deviation; ddof=1 uses sample standard deviation
        (null for fewer than two observations). Percentiles interpolate linearly at
        position (n-1)*p in sorted values. Apply the same conventions to every group.
        """
        if ddof not in (0, 1):
            raise ValueError("ddof must be 0 (population) or 1 (sample)")
        rows = self.tables[result]
        values = [self._column(result, row, field) for row in rows]
        if not all(numeric(v) for v in values):
            raise ValueError("statistics require finite numeric values")
        summary = {
            "result": result,
            "field": field,
            **summarize(values, ddof),
            "stddev_ddof": ddof,
            "percentile_method": "linear interpolation at (n-1)*p",
        }
        if group_by:
            groups = {}
            for row, value in zip(rows, values, strict=True):
                groups.setdefault(str(self._column(result, row, group_by)), []).append(
                    value
                )
            summary["groups"] = {
                key: summarize(items, ddof) for key, items in groups.items()
            }
        return summary

    def repeated_paths(
        self,
        nodes: str,
        label_fields: list,
        min_length: int = 2,
        max_length: int = 6,
        graph: str = "g0",
    ) -> dict:
        """Count repeated node-label paths using caller-selected nodes and label attributes.
        First connect each selected node to the nearest reachable selected nodes, traversing
        unselected intermediates and stopping at the next selected node. Then count simple
        paths of min_length..max_length SELECTED nodes. Node IDs distinguish occurrences;
        label attributes define repeated patterns. Parallel edges count once; undirected
        paths and their reversals count once. Overlapping paths count. Results include one
        concrete node-ID example per pattern. Above 100000 explored paths, narrow the query.
        Filter edges into an appropriate subgraph first to define which relations matter.
        """
        if not label_fields or not 2 <= min_length <= max_length <= 20:
            raise ValueError(
                "provide label_fields and 2 <= min_length <= max_length <= 20"
            )
        data, selected = self.graphs[graph], self._ids(nodes)
        if not selected <= set(data):
            raise ValueError("selected nodes must belong to the selected graph")
        labels = {
            n: json.dumps({k: data.nodes[n][k] for k in label_fields}, sort_keys=True)
            for n in selected
        }
        projected = nx.DiGraph() if data.is_directed() else nx.Graph()
        projected.add_nodes_from(selected)
        for source in selected:
            seen, pending = {source}, list(data.neighbors(source))
            while pending:
                target = pending.pop()
                if target in seen:
                    continue
                seen.add(target)
                if target in selected:
                    projected.add_edge(source, target)
                else:
                    pending.extend(data.neighbors(target))
        counts, examples, explored = Counter(), {}, 0
        for source in sorted(selected, key=str):
            pending = [(source,)]
            while pending:
                path = pending.pop()
                explored += 1
                if explored > 100000:
                    raise ValueError(
                        "path exploration exceeds 100000; narrow the graph or lengths"
                    )
                pattern = tuple(labels[n] for n in path)
                if len(path) >= min_length and (
                    data.is_directed()
                    or tuple(map(str, path)) <= tuple(map(str, reversed(path)))
                ):
                    example = path
                    if not data.is_directed() and pattern[::-1] < pattern:
                        pattern, example = pattern[::-1], path[::-1]
                    counts[pattern] += 1
                    examples.setdefault(pattern, list(example))
                if len(path) < max_length:
                    pending.extend(
                        (*path, n)
                        for n in projected.neighbors(path[-1])
                        if n not in path
                    )
        return self._store(
            [
                {
                    "path": [json.loads(label) for label in pattern],
                    "count": counts[pattern],
                    "example_ids": examples[pattern],
                }
                for pattern in sorted(counts, key=lambda p: (-counts[p], -len(p), p))
                if counts[pattern] >= 2
            ],
            min_length=min_length,
            max_length=max_length,
            overlapping=True,
        )

    def endpoints(self, terminal_filters: dict, graph: str = "g0") -> dict:
        """Find sinks (directed) or degree <= 1 nodes (undirected).
        Optional caller-supplied attribute predicates identify known terminal nodes.
        {} means terminal meaning is UNKNOWN. Endpoints never prove completion or deadlock.
        """
        data = self.graphs[graph]
        return self._store(
            [
                {
                    "id": n,
                    "attributes": dict(d),
                    "matches_terminal_filter": matches(d, terminal_filters)
                    if terminal_filters
                    else None,
                }
                for n, d in data.nodes(data=True)
                if (
                    data.out_degree(n) == 0
                    if data.is_directed()
                    else data.degree(n) <= 1
                )
            ],
            "nodes",
            definition="out_degree == 0" if data.is_directed() else "degree <= 1",
        )

    def rows(self, result: str, offset: int = 0, limit: int = 100) -> dict:
        """Read a table page. Fetch until next_offset=null when ALL results are requested."""
        if offset < 0 or not 1 <= limit <= 100:
            raise ValueError("require offset >= 0 and 1 <= limit <= 100")
        rows = self.tables[result]
        return {
            "result": result,
            "total": len(rows),
            "rows": rows[offset : offset + limit],
            "next_offset": offset + limit if offset + limit < len(rows) else None,
        }

    def tools(self) -> list[dict]:
        """Expose an explicitly bounded tool library; parameter names come from signatures."""
        types = {
            str: "string",
            int: "integer",
            dict: "object",
            list: "array",
            bool: "boolean",
            type(None): "null",
        }
        tools = []
        for name in self.tool_names:
            method = getattr(self, name)
            parameters = inspect.signature(method).parameters
            properties = {}
            for p in parameters.values():
                choices = get_args(p.annotation) or (p.annotation,)
                properties[p.name] = {
                    "type": types[choices[0]]
                    if len(choices) == 1
                    else [types[t] for t in choices]
                }
                if list in choices:
                    properties[p.name]["items"] = {"type": "string"}
            if name in {"algorithm", "algorithm_info"}:
                properties["name"]["enum"] = list(ALGORITHMS)
            tools.append(
                {
                    "type": "function",
                    "function": {
                        "name": name,
                        "description": inspect.getdoc(method),
                        "parameters": {
                            "type": "object",
                            "properties": properties,
                            "required": [
                                p.name
                                for p in parameters.values()
                                if p.default is inspect.Parameter.empty
                            ],
                            "additionalProperties": False,
                        },
                    },
                }
            )
        return tools

    def call(self, name: str, arguments: dict) -> dict:
        """Validate arguments and invoke an allowed operation, never arbitrary code."""
        if name not in self.tool_names:
            raise ValueError(f"unknown tool: {name}")
        method = getattr(self, name)
        signature = inspect.signature(method)
        bound = signature.bind(**arguments)
        for key, value in bound.arguments.items():
            annotation = signature.parameters[key].annotation
            choices = get_args(annotation) or (annotation,)
            if type(value) not in choices or (
                type(value) is list and not all(isinstance(v, str) for v in value)
            ):
                raise ValueError(f"invalid type for {key}")
        return method(**arguments)
