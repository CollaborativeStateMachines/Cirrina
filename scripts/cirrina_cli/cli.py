import typer

from .export_traces import export_traces

app = typer.Typer(
    no_args_is_help=True,
    add_completion=False,
    pretty_exceptions_show_locals=False,
)


@app.callback()
def main() -> None:
    pass


app.command("exportTraces")(export_traces)
