"""Sample demonstrating Python typing features for CodeFrame analysis."""
from __future__ import annotations

from abc import abstractmethod
from collections.abc import Iterable, Iterator, Mapping, Sequence
from dataclasses import dataclass
from typing import (
    Any,
    Callable,
    ClassVar,
    Final,
    Generic,
    Literal,
    NamedTuple,
    Optional,
    Protocol,
    TypeAlias,
    TypeVar,
    Union,
    overload,
    runtime_checkable,
)


# ---------------------------
# Type Variables
# ---------------------------

T = TypeVar("T")
K = TypeVar("K")
V = TypeVar("V")
T_co = TypeVar("T_co", covariant=True)
T_contra = TypeVar("T_contra", contravariant=True)
Numeric = TypeVar("Numeric", int, float)


# ---------------------------
# Type Aliases (TypeAlias annotation style)
# ---------------------------

JsonValue: TypeAlias = Union[str, int, float, bool, None, list["JsonValue"], dict[str, "JsonValue"]]
Callback: TypeAlias = Callable[[str, int], bool]
Matrix: TypeAlias = list[list[float]]


# ---------------------------
# Type Aliases (PEP 695 style - Python 3.12+)
# NOTE: These are currently NOT extracted by CodeFrame due to tree-sitter-python grammar limitations
# ---------------------------

type StringList = list[str]
type NumberPair = tuple[int, int]
type Handler[T] = Callable[[T], None]


# ---------------------------
# NamedTuple
# ---------------------------

class Point(NamedTuple):
    """A 2D point using NamedTuple."""
    x: float
    y: float

    def distance_from_origin(self) -> float:
        """Calculate distance from origin."""
        return (self.x ** 2 + self.y ** 2) ** 0.5

    def translate(self, dx: float, dy: float) -> Point:
        """Create a translated point."""
        return Point(self.x + dx, self.y + dy)


class Person(NamedTuple):
    """Person with optional fields."""
    name: str
    age: int
    email: str | None = None
    active: bool = True

    def greet(self) -> str:
        """Return a greeting."""
        return f"Hello, I'm {self.name}, {self.age} years old"


class Rectangle(NamedTuple):
    """A rectangle defined by two points."""
    top_left: Point
    bottom_right: Point

    @property
    def width(self) -> float:
        return abs(self.bottom_right.x - self.top_left.x)

    @property
    def height(self) -> float:
        return abs(self.bottom_right.y - self.top_left.y)

    def area(self) -> float:
        return self.width * self.height


# ---------------------------
# Protocol (Structural Subtyping)
# ---------------------------

class Drawable(Protocol):
    """Protocol for drawable objects."""

    def draw(self) -> str:
        """Draw the object and return representation."""
        ...


class Sizeable(Protocol):
    """Protocol for objects with size."""

    @property
    def size(self) -> int:
        """Get the size."""
        ...


@runtime_checkable
class Closeable(Protocol):
    """Protocol for closeable resources (runtime checkable)."""

    def close(self) -> None:
        """Close the resource."""
        ...


class Comparable(Protocol[T_contra]):
    """Protocol for comparable objects."""

    def __lt__(self, other: T_contra) -> bool:
        ...

    def __le__(self, other: T_contra) -> bool:
        ...


class SupportsAdd(Protocol[T_co]):
    """Protocol for objects supporting addition."""

    def __add__(self, other: Any) -> T_co:
        ...


# ---------------------------
# Generic Classes
# ---------------------------

class Stack(Generic[T]):
    """Generic stack implementation."""

    def __init__(self) -> None:
        self._items: list[T] = []

    def push(self, item: T) -> None:
        """Push an item onto the stack."""
        self._items.append(item)

    def pop(self) -> T:
        """Pop an item from the stack."""
        if not self._items:
            raise IndexError("Stack is empty")
        return self._items.pop()

    def peek(self) -> T | None:
        """Peek at the top item."""
        return self._items[-1] if self._items else None

    def is_empty(self) -> bool:
        """Check if the stack is empty."""
        return len(self._items) == 0


class Pair(Generic[K, V]):
    """Generic pair/tuple class."""

    def __init__(self, first: K, second: V) -> None:
        self._first = first
        self._second = second

    @property
    def first(self) -> K:
        return self._first

    @property
    def second(self) -> V:
        return self._second

    def swap(self) -> Pair[V, K]:
        """Create a new pair with swapped elements."""
        return Pair(self._second, self._first)

    def map_first(self, func: Callable[[K], T]) -> Pair[T, V]:
        """Map a function over the first element."""
        return Pair(func(self._first), self._second)


class Result(Generic[T]):
    """Result type representing success or failure."""

    def __init__(self, value: T | None = None, error: str | None = None) -> None:
        self._value = value
        self._error = error

    @classmethod
    def ok(cls, value: T) -> Result[T]:
        """Create a success result."""
        return cls(value=value)

    @classmethod
    def err(cls, error: str) -> Result[T]:
        """Create an error result."""
        return cls(error=error)

    def is_ok(self) -> bool:
        """Check if result is successful."""
        return self._error is None

    def unwrap(self) -> T:
        """Get the value or raise an error."""
        if self._error is not None:
            raise ValueError(self._error)
        return self._value  # type: ignore

    def unwrap_or(self, default: T) -> T:
        """Get the value or return default."""
        return self._value if self._error is None else default


# ---------------------------
# Class Variables and Final
# ---------------------------

class Configuration:
    """Class demonstrating ClassVar and Final."""

    VERSION: Final[str] = "1.0.0"
    _instance_count: ClassVar[int] = 0
    DEFAULT_TIMEOUT: ClassVar[int] = 30

    def __init__(self, name: str) -> None:
        Configuration._instance_count += 1
        self._name: Final[str] = name
        self._settings: dict[str, Any] = {}

    @classmethod
    def get_instance_count(cls) -> int:
        """Get the number of instances created."""
        return cls._instance_count

    def get_name(self) -> str:
        """Get the configuration name."""
        return self._name


# ---------------------------
# Literal Types
# ---------------------------

Direction = Literal["north", "south", "east", "west"]
HttpMethod = Literal["GET", "POST", "PUT", "DELETE", "PATCH"]
LogLevel = Literal["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]


class Router:
    """Demonstrates Literal types."""

    def navigate(self, direction: Direction) -> str:
        """Navigate in a direction."""
        return f"Moving {direction}"

    def request(self, method: HttpMethod, url: str) -> dict[str, Any]:
        """Make an HTTP request."""
        return {"method": method, "url": url, "status": 200}

    def log(self, level: LogLevel, message: str) -> None:
        """Log a message at the specified level."""
        print(f"[{level}] {message}")


# ---------------------------
# Overloaded Functions
# ---------------------------

class Converter:
    """Demonstrates function overloading."""

    @overload
    def convert(self, value: int) -> str:
        ...

    @overload
    def convert(self, value: str) -> int:
        ...

    @overload
    def convert(self, value: float) -> str:
        ...

    def convert(self, value: int | str | float) -> int | str:
        """Convert between types."""
        if isinstance(value, int):
            return str(value)
        elif isinstance(value, str):
            return int(value)
        else:
            return f"{value:.2f}"

    @overload
    def parse(self, data: str, as_list: Literal[True]) -> list[str]:
        ...

    @overload
    def parse(self, data: str, as_list: Literal[False]) -> str:
        ...

    @overload
    def parse(self, data: str) -> str:
        ...

    def parse(self, data: str, as_list: bool = False) -> str | list[str]:
        """Parse data optionally as list."""
        if as_list:
            return data.split(",")
        return data.strip()


# ---------------------------
# Generic Functions
# ---------------------------

def first(items: Sequence[T]) -> T | None:
    """Get the first item from a sequence."""
    return items[0] if items else None


def last(items: Sequence[T]) -> T | None:
    """Get the last item from a sequence."""
    return items[-1] if items else None


def identity(value: T) -> T:
    """Return the value unchanged."""
    return value


def swap(pair: tuple[K, V]) -> tuple[V, K]:
    """Swap elements of a tuple."""
    return (pair[1], pair[0])


def merge_dicts(d1: dict[K, V], d2: dict[K, V]) -> dict[K, V]:
    """Merge two dictionaries."""
    return {**d1, **d2}


def find_max(items: Iterable[Numeric]) -> Numeric | None:
    """Find the maximum of numeric items."""
    result: Numeric | None = None
    for item in items:
        if result is None or item > result:
            result = item
    return result


# ---------------------------
# Functions using Protocols
# ---------------------------

def draw_all(drawables: list[Drawable]) -> list[str]:
    """Draw all drawable objects."""
    return [d.draw() for d in drawables]


def total_size(items: list[Sizeable]) -> int:
    """Calculate total size of sizeable items."""
    return sum(item.size for item in items)


def close_all(resources: list[Closeable]) -> None:
    """Close all closeable resources."""
    for resource in resources:
        resource.close()


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    # NamedTuple
    p = Point(3.0, 4.0)
    print(f"Distance: {p.distance_from_origin()}")
    
    person = Person("Alice", 30, "alice@example.com")
    print(person.greet())
    
    # Generic Stack
    stack: Stack[int] = Stack()
    stack.push(1)
    stack.push(2)
    print(f"Popped: {stack.pop()}")
    
    # Pair
    pair = Pair("hello", 42)
    swapped = pair.swap()
    print(f"Swapped: {swapped.first}, {swapped.second}")
    
    # Result
    result = Result.ok(100)
    print(f"Value: {result.unwrap()}")
    
    # Converter with overloads
    converter = Converter()
    print(f"Int to str: {converter.convert(42)}")
    print(f"Str to int: {converter.convert('123')}")
    
    # Router with Literal types
    router = Router()
    print(router.navigate("north"))
