"""TI Python image API supplied by calculator firmware."""

from typing import TypeAlias, overload

RGB: TypeAlias = tuple[int, int, int]

class tiimageException(Exception): ...

def load_image(name: str) -> None:
    """Load a Python image variable by its calculator name."""
    ...

def show_image(x: int, y: int) -> None:
    """Draw the loaded image with its upper-left corner at ``(x, y)``."""
    ...

@overload
def clear_image() -> None: ...
@overload
def clear_image(x: int, y: int, width: int, height: int) -> None: ...
@overload
def clear_image(x: int, y: int, width: int, height: int, color: RGB) -> None: ...

def get_pixel(x: int, y: int) -> RGB:
    """Return the RGB color of a pixel in the image buffer."""
    ...

def set_pixel(x: int, y: int, color: RGB) -> None:
    """Set one image-buffer pixel to an RGB color."""
    ...

def show_screen() -> None:
    """Copy the image buffer to the calculator screen."""
    ...
