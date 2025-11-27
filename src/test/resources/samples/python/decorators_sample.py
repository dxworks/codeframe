"""Sample demonstrating Python decorators for CodeFrame analysis."""
from functools import wraps
from typing import Any, Callable, TypeVar

# File-level constants (captured in fields)
MAX_RETRIES: int = 3
DEFAULT_TIMEOUT: float = 30.0
APP_NAME: str = "DecoratorDemo"

# File-level call (captured in methodCalls)
T = TypeVar("T")


# ---------------------------
# Custom decorator functions
# ---------------------------

def log_calls(func: Callable[..., T]) -> Callable[..., T]:
    """Decorator that logs function calls."""
    @wraps(func)
    def wrapper(*args: Any, **kwargs: Any) -> T:
        print(f"Calling {func.__name__}")
        result = func(*args, **kwargs)
        print(f"Finished {func.__name__}")
        return result
    return wrapper


def retry(times: int = 3):
    """Decorator factory that retries a function on failure."""
    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        @wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> T:
            last_error: Exception | None = None
            for attempt in range(times):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    last_error = e
                    print(f"Attempt {attempt + 1} failed: {e}")
            raise last_error or RuntimeError("All retries failed")
        return wrapper
    return decorator


def validate_args(*validators: Callable[[Any], bool]):
    """Decorator factory that validates arguments."""
    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        @wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> T:
            for i, (arg, validator) in enumerate(zip(args, validators)):
                if not validator(arg):
                    raise ValueError(f"Argument {i} failed validation")
            return func(*args, **kwargs)
        return wrapper
    return decorator


# ---------------------------
# Class with method decorators
# ---------------------------

class Calculator:
    """Calculator demonstrating @staticmethod, @classmethod, and @property."""

    _instances: int = 0
    _precision: int = 2

    def __init__(self, name: str = "calc") -> None:
        self._name = name
        self._history: list[str] = []
        Calculator._instances += 1

    # Property getter
    @property
    def name(self) -> str:
        """Get the calculator name."""
        return self._name

    # Property setter
    @name.setter
    def name(self, value: str) -> None:
        """Set the calculator name."""
        if not value:
            raise ValueError("Name cannot be empty")
        self._name = value

    # Property deleter
    @name.deleter
    def name(self) -> None:
        """Reset the calculator name."""
        self._name = "unnamed"

    @property
    def history(self) -> list[str]:
        """Get calculation history (read-only)."""
        return self._history.copy()

    # Static method - no access to instance or class
    @staticmethod
    def add(a: float, b: float) -> float:
        """Add two numbers."""
        return a + b

    @staticmethod
    def subtract(a: float, b: float) -> float:
        """Subtract b from a."""
        return a - b

    # Class method - access to class, not instance
    @classmethod
    def get_instance_count(cls) -> int:
        """Get the number of Calculator instances."""
        return cls._instances

    @classmethod
    def set_precision(cls, precision: int) -> None:
        """Set the default precision for all calculators."""
        if precision < 0:
            raise ValueError("Precision must be non-negative")
        cls._precision = precision

    @classmethod
    def from_string(cls, config: str) -> "Calculator":
        """Create a Calculator from a config string."""
        name = config.split(":")[0] if ":" in config else config
        return cls(name=name)

    # Instance method with custom decorator
    @log_calls
    def multiply(self, a: float, b: float) -> float:
        """Multiply two numbers with logging."""
        result = a * b
        self._history.append(f"{a} * {b} = {result}")
        return round(result, self._precision)

    @retry(times=2)
    def divide(self, a: float, b: float) -> float:
        """Divide a by b with retry on failure."""
        if b == 0:
            raise ZeroDivisionError("Cannot divide by zero")
        result = a / b
        self._history.append(f"{a} / {b} = {result}")
        return round(result, self._precision)


# ---------------------------
# Class with stacked decorators
# ---------------------------

class MathUtils:
    """Utility class with stacked decorators."""

    @staticmethod
    @log_calls
    def power(base: float, exp: float) -> float:
        """Calculate base raised to exp."""
        return base ** exp

    @classmethod
    @retry(times=3)
    def parse_and_add(cls, a_str: str, b_str: str) -> float:
        """Parse strings and add them."""
        return float(a_str) + float(b_str)


# ---------------------------
# Decorated standalone functions
# ---------------------------

@log_calls
def greet(name: str) -> str:
    """Greet someone by name."""
    return f"Hello, {name}!"


@retry(times=5)
def fetch_data(url: str) -> dict[str, Any]:
    """Fetch data from a URL (simulated)."""
    if "error" in url:
        raise ConnectionError("Failed to connect")
    return {"url": url, "data": "sample"}


@validate_args(lambda x: x > 0, lambda x: x > 0)
def safe_divide(a: float, b: float) -> float:
    """Divide two positive numbers."""
    return a / b


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    calc = Calculator("MyCalc")
    print(f"Name: {calc.name}")
    calc.name = "UpdatedCalc"
    
    print(f"Add: {Calculator.add(10, 5)}")
    print(f"Instances: {Calculator.get_instance_count()}")
    
    calc2 = Calculator.from_string("Calc2:v1")
    print(f"Multiply: {calc.multiply(3, 4)}")
    
    print(greet("World"))
