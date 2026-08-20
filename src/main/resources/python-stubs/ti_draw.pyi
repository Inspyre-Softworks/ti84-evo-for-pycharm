"""TI-84 Evo drawing API.

These declarations are for editor completion and type checking. The real module is
provided by the calculator firmware.
"""

from typing import Literal, TypeAlias

Number: TypeAlias = int | float
PenSize: TypeAlias = Literal["thin", "medium", "thick"]
PenStyle: TypeAlias = Literal["solid", "dotted", "dashed"]

class tidrawException(Exception): ...

def clear() -> None:
    """Clear the entire drawing area to white."""
    ...

def clear_rect(x: Number, y: Number, w: Number, h: Number) -> None:
    """Erase a rectangular area to white without changing the drawing color."""
    ...

def draw_circle(x: Number, y: Number, r: Number) -> None:
    """Draw the outline of a circle centered at ``(x, y)``."""
    ...

def draw_line(x1: Number, y1: Number, x2: Number, y2: Number) -> None:
    """Draw a line between two points."""
    ...

def draw_poly(x_list: list[Number], y_list: list[Number]) -> None:
    """Draw connected segments through equal-length coordinate lists."""
    ...

def draw_rect(x: Number, y: Number, w: Number, h: Number) -> None:
    """Draw a rectangle outline using the current color and pen."""
    ...

def draw_text(x: Number, y: Number, string: str) -> None:
    """Draw text at the requested screen coordinate."""
    ...

def fill_circle(x: Number, y: Number, r: Number) -> None:
    """Draw a filled circle centered at ``(x, y)``."""
    ...

def fill_poly(x_list: list[Number], y_list: list[Number]) -> None:
    """Fill the polygon described by equal-length coordinate lists."""
    ...

def fill_rect(x: Number, y: Number, w: Number, h: Number) -> None:
    """Fill a rectangle using the current drawing color."""
    ...

def get_screen_dim() -> list[int]:
    """Return the largest drawable x and y coordinates: ``[319, 209]``."""
    ...

def plot_xy(x: Number, y: Number, shape: Literal[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]) -> None:
    """Draw one of the thirteen built-in markers centered at ``(x, y)``."""
    ...

def poly_xy(x: Number, y: Number, shape: Literal[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]) -> None:
    """Draw a built-in marker; TI-84 Plus CE Python name for ``plot_xy``."""
    ...

def set_color(r: int, g: int, b: int) -> None:
    """Set the drawing color using RGB components from 0 through 255."""
    ...

def set_pen(size: PenSize = "thin", style: PenStyle = "solid") -> None:
    """Set line thickness and style for subsequent outline operations."""
    ...

def set_window(xmin: Number, xmax: Number, ymin: Number, ymax: Number) -> None:
    """Map drawing coordinates to the requested horizontal and vertical ranges."""
    ...

def show_draw() -> None:
    """Pause until CLEAR is pressed."""
    ...
