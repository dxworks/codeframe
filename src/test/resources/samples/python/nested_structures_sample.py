"""Sample demonstrating nested structures in Python for CodeFrame analysis."""
from typing import Any, Callable, TypeVar

T = TypeVar("T")


# ---------------------------
# Nested Classes
# ---------------------------

class Outer:
    """Outer class containing nested classes."""

    class Inner:
        """Inner class nested within Outer."""

        def __init__(self, value: int) -> None:
            self._value = value

        def get_value(self) -> int:
            return self._value

        class DeepNested:
            """Deeply nested class."""

            def __init__(self, data: str) -> None:
                self._data = data

            def process(self) -> str:
                return self._data.upper()

    class AnotherInner:
        """Another inner class."""

        def __init__(self, name: str) -> None:
            self._name = name

        def greet(self) -> str:
            return f"Hello, {self._name}"

    def __init__(self, label: str) -> None:
        self._label = label
        self._inner: Outer.Inner | None = None

    def create_inner(self, value: int) -> "Outer.Inner":
        """Create an Inner instance."""
        self._inner = Outer.Inner(value)
        return self._inner

    def get_label(self) -> str:
        return self._label


# ---------------------------
# Class with nested factory
# ---------------------------

class Node:
    """Tree node with nested builder class."""

    class Builder:
        """Builder for creating Node instances."""

        def __init__(self) -> None:
            self._value: Any = None
            self._children: list["Node"] = []
            self._metadata: dict[str, Any] = {}

        def value(self, val: Any) -> "Node.Builder":
            """Set the node value."""
            self._value = val
            return self

        def add_child(self, child: "Node") -> "Node.Builder":
            """Add a child node."""
            self._children.append(child)
            return self

        def metadata(self, key: str, val: Any) -> "Node.Builder":
            """Add metadata."""
            self._metadata[key] = val
            return self

        def build(self) -> "Node":
            """Build the Node instance."""
            return Node(self._value, self._children, self._metadata)

    def __init__(
        self,
        value: Any,
        children: list["Node"] | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> None:
        self._value = value
        self._children = children or []
        self._metadata = metadata or {}

    @staticmethod
    def builder() -> "Node.Builder":
        """Create a new builder."""
        return Node.Builder()

    def traverse(self) -> list[Any]:
        """Traverse the tree depth-first."""
        result = [self._value]
        for child in self._children:
            result.extend(child.traverse())
        return result


# ---------------------------
# Nested Functions (Closures)
# ---------------------------

def make_counter(start: int = 0) -> Callable[[], int]:
    """Create a counter function (closure)."""
    count = start

    def counter() -> int:
        nonlocal count
        count += 1
        return count

    return counter


def make_multiplier(factor: int) -> Callable[[int], int]:
    """Create a multiplier function."""
    def multiply(x: int) -> int:
        return x * factor
    return multiply


def create_operations() -> dict[str, Callable[[int, int], int]]:
    """Create a dictionary of operations using nested functions."""

    def add(a: int, b: int) -> int:
        return a + b

    def subtract(a: int, b: int) -> int:
        return a - b

    def multiply(a: int, b: int) -> int:
        return a * b

    def divide(a: int, b: int) -> int:
        if b == 0:
            raise ValueError("Cannot divide by zero")
        return a // b

    return {
        "add": add,
        "subtract": subtract,
        "multiply": multiply,
        "divide": divide,
    }


def memoize(func: Callable[..., T]) -> Callable[..., T]:
    """Create a memoized version of a function using nested function."""
    cache: dict[tuple, T] = {}

    def wrapper(*args: Any) -> T:
        key = args
        if key not in cache:
            cache[key] = func(*args)
        return cache[key]

    wrapper.__name__ = func.__name__
    wrapper.__doc__ = func.__doc__
    return wrapper


# ---------------------------
# Lambda Functions
# ---------------------------

class ListProcessor:
    """Demonstrates lambda functions."""

    def __init__(self, items: list[Any]) -> None:
        self._items = items

    def filter_positive(self) -> list[int]:
        """Filter positive numbers using lambda."""
        return list(filter(lambda x: x > 0, self._items))

    def double_values(self) -> list[int]:
        """Double all values using lambda."""
        return list(map(lambda x: x * 2, self._items))

    def sort_by_length(self) -> list[str]:
        """Sort strings by length using lambda."""
        return sorted(self._items, key=lambda s: len(str(s)))

    def sort_by_key(self, key: str) -> list[dict]:
        """Sort dictionaries by a key using lambda."""
        return sorted(self._items, key=lambda d: d.get(key, 0))

    def reduce_sum(self) -> int:
        """Sum all items using reduce with lambda."""
        from functools import reduce
        return reduce(lambda acc, x: acc + x, self._items, 0)

    def find_first(self, predicate: Callable[[Any], bool]) -> Any | None:
        """Find first item matching predicate."""
        # Lambda used inline
        matches = filter(predicate, self._items)
        return next(matches, None)


# ---------------------------
# Comprehensions
# ---------------------------

class DataTransformer:
    """Demonstrates various comprehensions."""

    def __init__(self, data: list[Any]) -> None:
        self._data = data

    def list_comprehension(self) -> list[int]:
        """List comprehension with condition."""
        return [x * 2 for x in self._data if isinstance(x, int) and x > 0]

    def dict_comprehension(self) -> dict[str, int]:
        """Dictionary comprehension."""
        return {str(x): x ** 2 for x in self._data if isinstance(x, int)}

    def set_comprehension(self) -> set[int]:
        """Set comprehension."""
        return {abs(x) for x in self._data if isinstance(x, int)}

    def nested_comprehension(self) -> list[tuple[int, int]]:
        """Nested list comprehension."""
        return [(x, y) for x in range(3) for y in range(3) if x != y]

    def generator_expression(self) -> int:
        """Generator expression (lazy evaluation)."""
        return sum(x for x in self._data if isinstance(x, int))

    def conditional_expression(self) -> list[str]:
        """List comprehension with conditional expression."""
        return ["positive" if x > 0 else "non-positive" for x in self._data if isinstance(x, int)]


# ---------------------------
# Functions with default lambdas
# ---------------------------

def process_with_transform(
    items: list[int],
    transform: Callable[[int], int] = lambda x: x,
    filter_func: Callable[[int], bool] = lambda x: True,
) -> list[int]:
    """Process items with optional transform and filter."""
    return [transform(x) for x in items if filter_func(x)]


def sort_with_key(
    items: list[Any],
    key: Callable[[Any], Any] = lambda x: x,
    reverse: bool = False,
) -> list[Any]:
    """Sort items with a key function."""
    return sorted(items, key=key, reverse=reverse)


# ---------------------------
# Deeply nested function scope
# ---------------------------

def create_calculator():
    """Create a calculator with nested operation functions."""
    result = [0]  # Mutable container for nonlocal-like behavior

    def add(x: int) -> None:
        def do_add() -> None:
            result[0] += x
        do_add()

    def subtract(x: int) -> None:
        def do_subtract() -> None:
            result[0] -= x
        do_subtract()

    def get_result() -> int:
        return result[0]

    def reset() -> None:
        result[0] = 0

    return {
        "add": add,
        "subtract": subtract,
        "get_result": get_result,
        "reset": reset,
    }


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    # Nested classes
    outer = Outer("test")
    inner = outer.create_inner(42)
    print(f"Inner value: {inner.get_value()}")
    
    deep = Outer.Inner.DeepNested("hello")
    print(f"Deep nested: {deep.process()}")
    
    # Nested functions (closures)
    counter = make_counter(10)
    print(f"Counter: {counter()}, {counter()}")
    
    double = make_multiplier(2)
    print(f"Double 5: {double(5)}")
    
    # Lambda usage
    processor = ListProcessor([1, -2, 3, -4, 5])
    print(f"Positive: {processor.filter_positive()}")
    print(f"Doubled: {processor.double_values()}")
    
    # Comprehensions
    transformer = DataTransformer([1, 2, 3, 4, 5])
    print(f"List comp: {transformer.list_comprehension()}")
    print(f"Dict comp: {transformer.dict_comprehension()}")
    
    # Calculator
    calc = create_calculator()
    calc["add"](10)
    calc["subtract"](3)
    print(f"Result: {calc['get_result']()}")
