"""TI-Innovator Hub command API supplied by calculator firmware."""

from typing import Any, TypeAlias

Number: TypeAlias = int | float

class tihubException(Exception): ...

def connect() -> None:
    """Connect to the attached TI-Innovator Hub."""
    ...

def disconnect() -> None:
    """Disconnect from the attached Hub."""
    ...

def set(command: str) -> None:
    """Send a SET command to the Hub."""
    ...

def read(command: str) -> Any:
    """Send a READ command and return the Hub response."""
    ...

def calibrate(command: str) -> None:
    """Send a CALIBRATE command to a supported sensor."""
    ...

def range(command: str) -> Any:
    """Configure or query a sensor range."""
    ...

def version() -> str:
    """Return the Hub firmware version."""
    ...

def begin() -> None:
    """Begin a Hub command session."""
    ...

def start() -> None:
    """Start a configured Hub operation."""
    ...

def about() -> str:
    """Return descriptive Hub information."""
    ...

def isti() -> bool:
    """Return whether the connected device identifies as a TI Hub."""
    ...

def what() -> str:
    """Return the connected Hub device type."""
    ...

def who() -> str:
    """Return identifying information from the connected Hub."""
    ...

def last_error() -> str:
    """Return the most recent Hub error message."""
    ...

def sleep(seconds: Number) -> None:
    """Pause for the requested number of seconds."""
    ...

def wait(seconds: Number) -> None:
    """Wait before continuing the Hub program."""
    ...

def get(command: str) -> Any:
    """Issue a GET command and return its response."""
    ...

def send(command: str) -> None:
    """Send a raw command to the Hub."""
    ...
