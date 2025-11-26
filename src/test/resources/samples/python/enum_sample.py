"""Sample demonstrating Python enums for CodeFrame analysis."""
from enum import Enum, IntEnum, Flag, auto, unique
from typing import Optional


# ---------------------------
# Basic Enum
# ---------------------------

class Color(Enum):
    """Simple color enumeration."""
    RED = 1
    GREEN = 2
    BLUE = 3

    def describe(self) -> str:
        """Return a description of the color."""
        return f"Color: {self.name} (value={self.value})"


# ---------------------------
# String Enum (Python 3.11+)
# ---------------------------

class Status(str, Enum):
    """Status enumeration that also behaves like a string."""
    PENDING = "pending"
    ACTIVE = "active"
    COMPLETED = "completed"
    CANCELLED = "cancelled"

    @classmethod
    def from_string(cls, value: str) -> "Status":
        """Create a Status from a string value."""
        for status in cls:
            if status.value == value.lower():
                return status
        raise ValueError(f"Unknown status: {value}")

    def is_terminal(self) -> bool:
        """Check if this is a terminal status."""
        return self in (Status.COMPLETED, Status.CANCELLED)


# ---------------------------
# IntEnum - can be used as int
# ---------------------------

class Priority(IntEnum):
    """Priority levels that can be compared numerically."""
    LOW = 1
    MEDIUM = 2
    HIGH = 3
    CRITICAL = 4

    def __str__(self) -> str:
        return f"Priority.{self.name}"

    def is_urgent(self) -> bool:
        """Check if priority is urgent (HIGH or above)."""
        return self >= Priority.HIGH


# ---------------------------
# Unique Enum
# ---------------------------

@unique
class ErrorCode(IntEnum):
    """Error codes that must be unique."""
    SUCCESS = 0
    NOT_FOUND = 404
    SERVER_ERROR = 500
    UNAUTHORIZED = 401
    FORBIDDEN = 403

    @property
    def is_error(self) -> bool:
        """Check if this code represents an error."""
        return self.value >= 400


# ---------------------------
# Flag Enum (bitwise operations)
# ---------------------------

class Permission(Flag):
    """Permission flags that can be combined."""
    NONE = 0
    READ = auto()
    WRITE = auto()
    EXECUTE = auto()
    DELETE = auto()
    
    # Composite flags
    READ_WRITE = READ | WRITE
    ALL = READ | WRITE | EXECUTE | DELETE

    def can_read(self) -> bool:
        """Check if read permission is granted."""
        return Permission.READ in self

    def can_write(self) -> bool:
        """Check if write permission is granted."""
        return Permission.WRITE in self


# ---------------------------
# Enum with auto()
# ---------------------------

class Direction(Enum):
    """Direction enum using auto() for values."""
    NORTH = auto()
    SOUTH = auto()
    EAST = auto()
    WEST = auto()

    def opposite(self) -> "Direction":
        """Get the opposite direction."""
        opposites = {
            Direction.NORTH: Direction.SOUTH,
            Direction.SOUTH: Direction.NORTH,
            Direction.EAST: Direction.WEST,
            Direction.WEST: Direction.EAST,
        }
        return opposites[self]


# ---------------------------
# Complex Enum with __init__
# ---------------------------

class Planet(Enum):
    """Planets with mass and radius data."""
    MERCURY = (3.303e+23, 2.4397e6)
    VENUS = (4.869e+24, 6.0518e6)
    EARTH = (5.976e+24, 6.37814e6)
    MARS = (6.421e+23, 3.3972e6)

    def __init__(self, mass: float, radius: float) -> None:
        self._mass = mass
        self._radius = radius

    @property
    def mass(self) -> float:
        """Get the planet's mass in kg."""
        return self._mass

    @property
    def radius(self) -> float:
        """Get the planet's radius in meters."""
        return self._radius

    @property
    def surface_gravity(self) -> float:
        """Calculate surface gravity."""
        G = 6.67300e-11
        return G * self._mass / (self._radius ** 2)


# ---------------------------
# Utility functions
# ---------------------------

def get_status_label(status: Status) -> str:
    """Get a human-readable label for a status."""
    labels = {
        Status.PENDING: "Waiting",
        Status.ACTIVE: "In Progress",
        Status.COMPLETED: "Done",
        Status.CANCELLED: "Cancelled",
    }
    return labels.get(status, "Unknown")


def combine_permissions(perms: list[Permission]) -> Permission:
    """Combine multiple permissions into one."""
    result = Permission.NONE
    for perm in perms:
        result = result | perm
    return result


def find_planet_by_mass(min_mass: float) -> Optional[Planet]:
    """Find the first planet with mass greater than min_mass."""
    for planet in Planet:
        if planet.mass > min_mass:
            return planet
    return None


# ---------------------------
# Example usage
# ---------------------------

if __name__ == "__main__":
    # Basic enum
    print(Color.RED.describe())
    
    # Status enum
    status = Status.from_string("active")
    print(f"Status: {status}, terminal: {status.is_terminal()}")
    
    # Priority comparison
    if Priority.HIGH > Priority.MEDIUM:
        print("HIGH is greater than MEDIUM")
    
    # Flag combination
    perms = Permission.READ | Permission.WRITE
    print(f"Can read: {perms.can_read()}, Can write: {perms.can_write()}")
    
    # Planet data
    print(f"Earth gravity: {Planet.EARTH.surface_gravity:.2f} m/s²")
