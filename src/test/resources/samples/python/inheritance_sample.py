"""Sample demonstrating Python inheritance patterns for CodeFrame analysis."""
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Any, Optional


# ---------------------------
# Abstract Base Class
# ---------------------------

class Animal(ABC):
    """Abstract base class for animals."""

    def __init__(self, name: str, age: int) -> None:
        self._name = name
        self._age = age

    @property
    def name(self) -> str:
        return self._name

    @property
    def age(self) -> int:
        return self._age

    @abstractmethod
    def speak(self) -> str:
        """Make the animal's sound."""
        ...

    @abstractmethod
    def move(self) -> str:
        """Describe how the animal moves."""
        ...

    def describe(self) -> str:
        """Describe the animal."""
        return f"{self._name} is a {self.age}-year-old {type(self).__name__}"


# ---------------------------
# Simple Inheritance
# ---------------------------

class Dog(Animal):
    """A dog is an animal."""

    def __init__(self, name: str, age: int, breed: str) -> None:
        super().__init__(name, age)
        self._breed = breed

    @property
    def breed(self) -> str:
        return self._breed

    def speak(self) -> str:
        return "Woof!"

    def move(self) -> str:
        return "runs on four legs"

    def fetch(self, item: str) -> str:
        """Dogs can fetch things."""
        return f"{self._name} fetches the {item}"


class Cat(Animal):
    """A cat is an animal."""

    def __init__(self, name: str, age: int, indoor: bool = True) -> None:
        super().__init__(name, age)
        self._indoor = indoor

    @property
    def indoor(self) -> bool:
        return self._indoor

    def speak(self) -> str:
        return "Meow!"

    def move(self) -> str:
        return "prowls silently"

    def purr(self) -> str:
        """Cats can purr."""
        return f"{self._name} purrs contentedly"


# ---------------------------
# Mixin Classes
# ---------------------------

class SerializableMixin:
    """Mixin providing serialization capabilities."""

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        result: dict[str, Any] = {}
        for key, value in self.__dict__.items():
            clean_key = key.lstrip("_")
            if isinstance(value, datetime):
                result[clean_key] = value.isoformat()
            else:
                result[clean_key] = value
        return result

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "SerializableMixin":
        """Create instance from dictionary."""
        return cls(**data)


class ComparableMixin:
    """Mixin providing comparison capabilities."""

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, self.__class__):
            return NotImplemented
        return self.__dict__ == other.__dict__

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, self.__class__):
            return NotImplemented
        return str(self) < str(other)

    def __le__(self, other: object) -> bool:
        return self == other or self < other

    def __gt__(self, other: object) -> bool:
        if not isinstance(other, self.__class__):
            return NotImplemented
        return str(self) > str(other)

    def __ge__(self, other: object) -> bool:
        return self == other or self > other


class LoggableMixin:
    """Mixin providing logging capabilities."""

    _log: list[str] = []

    def log(self, message: str) -> None:
        """Log a message."""
        timestamp = datetime.now().isoformat()
        entry = f"[{timestamp}] {type(self).__name__}: {message}"
        self._log.append(entry)

    def get_logs(self) -> list[str]:
        """Get all log entries."""
        return self._log.copy()

    def clear_logs(self) -> None:
        """Clear all log entries."""
        self._log.clear()


class ValidatableMixin:
    """Mixin providing validation capabilities."""

    def validate(self) -> list[str]:
        """Validate the object and return list of errors."""
        errors: list[str] = []
        for key, value in self.__dict__.items():
            if value is None:
                errors.append(f"{key} cannot be None")
        return errors

    def is_valid(self) -> bool:
        """Check if the object is valid."""
        return len(self.validate()) == 0


# ---------------------------
# Multiple Inheritance with Mixins
# ---------------------------

class Entity(SerializableMixin, ComparableMixin, ValidatableMixin):
    """Base entity with serialization, comparison, and validation."""

    def __init__(self, id: int, created_at: Optional[datetime] = None) -> None:
        self._id = id
        self._created_at = created_at or datetime.now()

    @property
    def id(self) -> int:
        return self._id

    @property
    def created_at(self) -> datetime:
        return self._created_at

    def __str__(self) -> str:
        return f"{type(self).__name__}(id={self._id})"


class User(Entity, LoggableMixin):
    """User entity with logging."""

    def __init__(
        self,
        id: int,
        username: str,
        email: str,
        created_at: Optional[datetime] = None,
    ) -> None:
        super().__init__(id, created_at)
        self._username = username
        self._email = email

    @property
    def username(self) -> str:
        return self._username

    @property
    def email(self) -> str:
        return self._email

    def change_email(self, new_email: str) -> None:
        """Change user's email."""
        old_email = self._email
        self._email = new_email
        self.log(f"Email changed from {old_email} to {new_email}")

    def validate(self) -> list[str]:
        """Validate user data."""
        errors = super().validate()
        if "@" not in self._email:
            errors.append("Invalid email format")
        if len(self._username) < 3:
            errors.append("Username must be at least 3 characters")
        return errors


class Product(Entity):
    """Product entity."""

    def __init__(
        self,
        id: int,
        name: str,
        price: float,
        created_at: Optional[datetime] = None,
    ) -> None:
        super().__init__(id, created_at)
        self._name = name
        self._price = price

    @property
    def name(self) -> str:
        return self._name

    @property
    def price(self) -> float:
        return self._price

    def validate(self) -> list[str]:
        """Validate product data."""
        errors = super().validate()
        if self._price < 0:
            errors.append("Price cannot be negative")
        if not self._name:
            errors.append("Name cannot be empty")
        return errors


# ---------------------------
# Diamond Inheritance (MRO demonstration)
# ---------------------------

class A:
    """Base class A."""

    def method(self) -> str:
        return "A"

    def greet(self) -> str:
        return "Hello from A"


class B(A):
    """Class B extends A."""

    def method(self) -> str:
        return f"B -> {super().method()}"

    def greet(self) -> str:
        return "Hello from B"


class C(A):
    """Class C extends A."""

    def method(self) -> str:
        return f"C -> {super().method()}"

    def greet(self) -> str:
        return "Hello from C"


class D(B, C):
    """Class D extends B and C (diamond inheritance)."""

    def method(self) -> str:
        return f"D -> {super().method()}"

    def show_mro(self) -> list[str]:
        """Show the Method Resolution Order."""
        return [cls.__name__ for cls in type(self).__mro__]


# ---------------------------
# Using __slots__ for memory efficiency
# ---------------------------

class SlottedBase:
    """Base class using __slots__."""
    __slots__ = ("_x", "_y")

    def __init__(self, x: int, y: int) -> None:
        self._x = x
        self._y = y

    @property
    def x(self) -> int:
        return self._x

    @property
    def y(self) -> int:
        return self._y


class SlottedChild(SlottedBase):
    """Child class extending slots."""
    __slots__ = ("_z",)

    def __init__(self, x: int, y: int, z: int) -> None:
        super().__init__(x, y)
        self._z = z

    @property
    def z(self) -> int:
        return self._z

    def coordinates(self) -> tuple[int, int, int]:
        """Get all coordinates."""
        return (self._x, self._y, self._z)


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    # Simple inheritance
    dog = Dog("Buddy", 3, "Labrador")
    print(dog.describe())
    print(dog.speak())
    
    # Multiple inheritance
    user = User(1, "john_doe", "john@example.com")
    print(user.to_dict())
    print(f"Valid: {user.is_valid()}")
    
    # Diamond inheritance
    d = D()
    print(f"Method: {d.method()}")
    print(f"MRO: {d.show_mro()}")
    
    # Slotted classes
    point = SlottedChild(1, 2, 3)
    print(f"Coordinates: {point.coordinates()}")
