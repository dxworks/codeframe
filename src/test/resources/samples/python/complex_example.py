from __future__ import annotations

import abc
import asyncio
import contextlib
import math
import re
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, Generator, Iterable, List, Optional, Tuple


# ---------------------------
# Configuration via dataclass
# ---------------------------

@dataclass(frozen=True)
class Config:
    name: str
    version: str = "1.0"
    debug: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)


# ---------------------------
# Repository abstraction
# ---------------------------

class Repository(abc.ABC):
    """Abstract repository for key-value items.

    Implementations should override public methods and may use private helpers.
    """

    def __init__(self, config: Config) -> None:
        self._config = config
        self._created_at = datetime.utcnow()

    # Public API
    def put(self, key: str, value: Any) -> None:
        key_n = self._normalize_key(key)
        self._validate(key_n, value)
        self._put_impl(key_n, value)

    def get(self, key: str) -> Optional[Any]:
        key_n = self._normalize_key(key)
        return self._get_impl(key_n)

    def delete(self, key: str) -> bool:
        key_n = self._normalize_key(key)
        return self._delete_impl(key_n)

    def keys(self) -> List[str]:
        return list(self._keys_impl())

    # Private helpers
    def _normalize_key(self, key: str) -> str:
        return key.strip().lower()

    def _validate(self, key: str, value: Any) -> None:
        if not key:
            raise ValueError("Key cannot be empty")
        if value is None:
            raise ValueError("Value cannot be None")

    # Methods for implementers
    @abc.abstractmethod
    def _put_impl(self, key: str, value: Any) -> None: ...

    @abc.abstractmethod
    def _get_impl(self, key: str) -> Optional[Any]: ...

    @abc.abstractmethod
    def _delete_impl(self, key: str) -> bool: ...

    @abc.abstractmethod
    def _keys_impl(self) -> Iterable[str]: ...


class InMemoryRepository(Repository):
    """Concrete Repository that stores items in memory."""

    def __init__(self, config: Config) -> None:
        super().__init__(config)
        self.__store: Dict[str, Any] = {}

    # Public convenience
    def upsert(self, key: str, value: Any) -> None:
        self.put(key, value)

    # Implementer methods
    def _put_impl(self, key: str, value: Any) -> None:
        self.__store[key] = value
        if self._config.debug:
            print(f"[DEBUG] PUT {key}={value}")

    def _get_impl(self, key: str) -> Optional[Any]:
        return self.__store.get(key)

    def _delete_impl(self, key: str) -> bool:
        existed = key in self.__store
        self.__store.pop(key, None)
        if self._config.debug:
            print(f"[DEBUG] DEL {key} -> {existed}")
        return existed

    def _keys_impl(self) -> Iterable[str]:
        return self.__store.keys()


# ---------------------------
# Context manager
# ---------------------------

class ResourceManager:
    """Simple context manager emulating resource acquisition and release."""

    def __init__(self, label: str) -> None:
        self._label = label
        self._active = False

    def __enter__(self) -> "ResourceManager":
        self._active = True
        print(f"[{self._label}] acquired")
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self._active = False
        print(f"[{self._label}] released")

    # Public and private methods
    def is_active(self) -> bool:
        return self._active

    def _tag(self) -> str:
        return f"rm:{self._label}"


# ---------------------------
# Generator + utilities
# ---------------------------

def batched(iterable: Iterable[Any], size: int) -> Generator[List[Any], None, None]:
    """Yield lists of up to `size` items from `iterable`."""
    if size <= 0:
        raise ValueError("size must be > 0")
    batch: List[Any] = []
    for item in iterable:
        batch.append(item)
        if len(batch) >= size:
            yield batch
            batch = []
    if batch:
        yield batch


def slugify(text: str) -> str:
    """Return a URL-friendly slug from text."""
    text = text.strip().lower()
    text = re.sub(r"[^a-z0-9\-\s]", "", text)
    text = re.sub(r"\s+", "-", text)
    return re.sub(r"-+", "-", text).strip("-")


def safe_divide(a: float, b: float) -> float:
    """Divide a by b, returning 0.0 for division by zero."""
    if b == 0:
        return 0.0
    return a / b


# ---------------------------
# Async API
# ---------------------------

async def fetch_data_async(n: int = 3, delay: float = 0.05) -> List[int]:
    """Simulate async IO by sleeping and returning a list of ints."""
    await asyncio.sleep(delay)
    return list(range(n))


# ---------------------------
# Example usage (not executed in tests)
# ---------------------------

if __name__ == "__main__":
    cfg = Config(name="example", debug=True)
    repo = InMemoryRepository(cfg)
    repo.put("Foo", {"x": 1})
    repo.upsert("Bar", 42)
    print("keys:", repo.keys())
    print("bar:", repo.get("bar"))

    print("slug:", slugify("Hello, World! 2025"))
    print("safe_divide(1,0):", safe_divide(1, 0))

    with ResourceManager("res") as rm:
        print("active:", rm.is_active(), "tag:", rm._tag())

    async def _demo():
        print("async:", await fetch_data_async(5, 0.01))

    asyncio.run(_demo())
