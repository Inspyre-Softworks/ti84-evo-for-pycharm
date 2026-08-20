"""TI-84 Evo screen, keyboard, and calculator-data API.

These declarations are for editor completion and type checking. The real module is
provided by the calculator firmware.
"""

from typing import Literal, TypeAlias, overload

Number: TypeAlias = int | float
Alignment: TypeAlias = Literal["left", "center", "right"]

def disp_cursor(enabled: int) -> None:
    """Show the text cursor when nonzero and hide it when zero."""
    ...

@overload
def disp_at(row: int, text: str, align: Alignment = "left") -> None: ...
@overload
def disp_at(row: int, col: int, text: str) -> None: ...

def disp_clr(row: int | None = None) -> None:
    """Clear the whole text screen, or only the requested row."""
    ...

def disp_wait() -> None:
    """Hold the text screen until CLEAR is pressed, then clear it."""
    ...

def escape() -> bool:
    """Return whether CLEAR is currently pressed."""
    ...

def get_key(wait: int) -> int:
    """Return a key code; zero polls and a nonzero value waits for a key."""
    ...

def recall_RegEQ() -> str:
    """Return the calculator's most recently computed regression equation."""
    ...

def recall_list(name: str) -> list[float]:
    """Read a TI-Basic list variable from calculator memory."""
    ...

def sleep(seconds: Number) -> None:
    """Pause for the requested number of seconds."""
    ...

def store_list(name: str, values: list[Number]) -> None:
    """Store up to 100 numeric values in a TI-Basic list variable."""
    ...

def wait(seconds: Number) -> None:
    """Pause for the requested number of seconds."""
    ...

def wait_key() -> int:
    """Wait for a key press and return its numeric key code."""
    ...
