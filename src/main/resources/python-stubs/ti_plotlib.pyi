"""TI Python plotting API supplied by calculator firmware."""

from typing import Literal, TypeAlias, overload

Number: TypeAlias = int | float
Alignment: TypeAlias = Literal["left", "center", "right"]
PenSize: TypeAlias = Literal["thin", "medium", "thick"]
PenStyle: TypeAlias = Literal["solid", "dot", "dash"]

class tiplotlibException(Exception): ...

xmin: float
xmax: float
ymin: float
ymax: float
xscl: float
yscl: float
a: float
m: float
b: float

def cls() -> None:
    """Clear the plot window."""
    ...

def grid(x_scale: Number, y_scale: Number, style: str = "dot") -> None:
    """Configure grid spacing and style."""
    ...

def window(x_min: Number, x_max: Number, y_min: Number, y_max: Number) -> None:
    """Set the plotting window."""
    ...

def auto_window(x_list: list[Number], y_list: list[Number]) -> None:
    """Choose a window that contains all supplied points."""
    ...

def axes(mode: str = "on") -> None:
    """Configure plot axes."""
    ...

def labels(x_label: str, y_label: str, x: int = 12, y: int = 2) -> None:
    """Label the horizontal and vertical axes."""
    ...

def title(text: str) -> None:
    """Set the plot title."""
    ...

def show_plot() -> None:
    """Display the completed plot."""
    ...

def use_buffer() -> None:
    """Render subsequent operations into the graphics buffer."""
    ...

def color(red: int, green: int, blue: int) -> None:
    """Set the plot color with RGB components from 0 through 255."""
    ...

def colour(red: int, green: int, blue: int) -> None:
    """British-English alias for ``color`` on supported TI firmware."""
    ...

def scatter(x_list: list[Number], y_list: list[Number], mark: str = "o") -> None:
    """Draw a scatter plot from paired coordinate lists."""
    ...

@overload
def plot(x_list: list[Number], y_list: list[Number], mark: str = "") -> None: ...
@overload
def plot(x: Number, y: Number, mark: str = "o") -> None: ...

def line(
    x1: Number,
    y1: Number,
    x2: Number,
    y2: Number,
    mode: Literal["", "arrow"] = "",
) -> None:
    """Draw a line, optionally with an arrow head."""
    ...

def lin_reg(
    x_list: list[Number],
    y_list: list[Number],
    display: Alignment = "center",
    row: int = 11,
) -> None:
    """Calculate and optionally display a linear regression."""
    ...

def pen(size: PenSize = "thin", style: PenStyle = "solid") -> None:
    """Set plot line thickness and style."""
    ...

def text_at(row: int, text: str, align: Alignment = "left") -> None:
    """Draw aligned text on a plot row."""
    ...
