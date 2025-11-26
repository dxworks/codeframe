"""Sample demonstrating Python exception handling for CodeFrame analysis."""
from typing import Any, Optional
import logging

logger = logging.getLogger(__name__)


# ---------------------------
# Custom Exceptions
# ---------------------------

class ApplicationError(Exception):
    """Base exception for application errors."""

    def __init__(self, message: str, code: int = 0) -> None:
        super().__init__(message)
        self.code = code
        self.message = message

    def __str__(self) -> str:
        return f"[{self.code}] {self.message}"


class ValidationError(ApplicationError):
    """Raised when validation fails."""

    def __init__(self, field: str, message: str) -> None:
        super().__init__(f"{field}: {message}", code=400)
        self.field = field


class NotFoundError(ApplicationError):
    """Raised when a resource is not found."""

    def __init__(self, resource: str, identifier: Any) -> None:
        super().__init__(f"{resource} not found: {identifier}", code=404)
        self.resource = resource
        self.identifier = identifier


class AuthenticationError(ApplicationError):
    """Raised when authentication fails."""

    def __init__(self, reason: str = "Invalid credentials") -> None:
        super().__init__(reason, code=401)


class RetryableError(ApplicationError):
    """Raised for errors that can be retried."""

    def __init__(self, message: str, retry_after: int = 5) -> None:
        super().__init__(message, code=503)
        self.retry_after = retry_after


# ---------------------------
# Exception handling patterns
# ---------------------------

class DataProcessor:
    """Demonstrates various exception handling patterns."""

    def __init__(self, strict: bool = False) -> None:
        self._strict = strict
        self._errors: list[Exception] = []

    # Basic try-except
    def parse_int(self, value: str) -> Optional[int]:
        """Parse an integer with basic exception handling."""
        try:
            return int(value)
        except ValueError:
            return None

    # Multiple except clauses
    def parse_number(self, value: str) -> float:
        """Parse a number with multiple exception handlers."""
        try:
            return float(value)
        except ValueError as e:
            logger.warning(f"Invalid number format: {value}")
            raise ValidationError("value", str(e))
        except TypeError as e:
            logger.error(f"Type error: {e}")
            raise ValidationError("value", "Must be a string")

    # Try-except-else
    def safe_divide(self, a: float, b: float) -> Optional[float]:
        """Divide with else clause for success case."""
        try:
            result = a / b
        except ZeroDivisionError:
            logger.warning("Division by zero attempted")
            return None
        else:
            logger.info(f"Division successful: {a}/{b}={result}")
            return result

    # Try-except-finally
    def process_file(self, path: str) -> list[str]:
        """Process a file with cleanup in finally."""
        handle = None
        lines: list[str] = []
        try:
            handle = open(path, "r")
            lines = handle.readlines()
        except FileNotFoundError:
            raise NotFoundError("File", path)
        except PermissionError:
            raise ApplicationError(f"Permission denied: {path}", code=403)
        finally:
            if handle is not None:
                handle.close()
                logger.debug(f"Closed file handle: {path}")
        return lines

    # Try-except-else-finally (full form)
    def fetch_data(self, key: str) -> dict[str, Any]:
        """Fetch data with complete exception handling structure."""
        data: dict[str, Any] = {}
        acquired = False
        try:
            if not key:
                raise ValidationError("key", "Cannot be empty")
            acquired = True
            data = {"key": key, "value": hash(key)}
        except ValidationError:
            raise
        except Exception as e:
            logger.exception(f"Unexpected error fetching {key}")
            raise ApplicationError(f"Fetch failed: {e}", code=500)
        else:
            logger.info(f"Successfully fetched data for {key}")
        finally:
            if acquired:
                logger.debug("Releasing resources")
        return data

    # Re-raising exceptions
    def validate_and_process(self, data: dict[str, Any]) -> dict[str, Any]:
        """Validate data and re-raise with context."""
        try:
            if "id" not in data:
                raise ValidationError("id", "Required field missing")
            if not isinstance(data["id"], int):
                raise ValidationError("id", "Must be an integer")
            return {"processed": True, **data}
        except ValidationError:
            raise  # Re-raise as-is
        except Exception as e:
            raise ApplicationError(f"Processing failed: {e}") from e

    # Exception chaining
    def load_config(self, path: str) -> dict[str, Any]:
        """Load config with exception chaining."""
        try:
            with open(path) as f:
                import json
                return json.load(f)
        except FileNotFoundError as e:
            raise NotFoundError("Config", path) from e
        except json.JSONDecodeError as e:
            raise ValidationError("config", f"Invalid JSON: {e}") from e

    # Catching multiple exceptions in one clause
    def convert_value(self, value: Any) -> str:
        """Convert value with grouped exception handling."""
        try:
            if isinstance(value, bytes):
                return value.decode("utf-8")
            return str(value)
        except (UnicodeDecodeError, AttributeError) as e:
            return f"<unconvertible: {type(value).__name__}>"

    # Bare except (not recommended, but exists)
    def risky_operation(self, value: Any) -> bool:
        """Demonstrate bare except (avoid in production)."""
        try:
            _ = value.some_method()
            return True
        except:  # noqa: E722
            return False

    # Collecting errors without stopping
    def process_batch(self, items: list[Any]) -> list[Any]:
        """Process items, collecting errors for later."""
        results: list[Any] = []
        self._errors.clear()
        
        for item in items:
            try:
                result = self._process_single(item)
                results.append(result)
            except Exception as e:
                self._errors.append(e)
                if self._strict:
                    raise
        
        return results

    def _process_single(self, item: Any) -> Any:
        """Process a single item."""
        if item is None:
            raise ValidationError("item", "Cannot be None")
        return {"item": item, "processed": True}

    @property
    def errors(self) -> list[Exception]:
        """Get collected errors."""
        return self._errors.copy()


# ---------------------------
# Context manager with exceptions
# ---------------------------

class TransactionContext:
    """Context manager demonstrating exception handling."""

    def __init__(self, name: str) -> None:
        self._name = name
        self._committed = False

    def __enter__(self) -> "TransactionContext":
        logger.info(f"Starting transaction: {self._name}")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> bool:
        if exc_type is None:
            logger.info(f"Committing transaction: {self._name}")
            self._committed = True
        else:
            logger.error(f"Rolling back transaction: {self._name} due to {exc_type.__name__}")
            # Return False to propagate the exception
        return False

    def execute(self, operation: str) -> None:
        """Execute an operation within the transaction."""
        if operation == "fail":
            raise ApplicationError("Operation failed")
        logger.info(f"Executed: {operation}")


# ---------------------------
# Utility functions
# ---------------------------

def assert_not_none(value: Any, name: str) -> Any:
    """Assert that a value is not None."""
    if value is None:
        raise ValidationError(name, "Value cannot be None")
    return value


def wrap_exception(func):
    """Decorator that wraps exceptions in ApplicationError."""
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except ApplicationError:
            raise
        except Exception as e:
            raise ApplicationError(f"Unexpected error: {e}") from e
    return wrapper


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    processor = DataProcessor()
    
    # Basic parsing
    print(processor.parse_int("42"))
    print(processor.parse_int("not a number"))
    
    # Division with handling
    print(processor.safe_divide(10, 2))
    print(processor.safe_divide(10, 0))
    
    # Transaction context
    try:
        with TransactionContext("test") as tx:
            tx.execute("step1")
            tx.execute("step2")
    except ApplicationError as e:
        print(f"Transaction failed: {e}")
