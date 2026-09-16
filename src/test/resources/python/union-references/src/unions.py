from typing import Any, Callable

from .types import Bar, Foo


class Example:
    maybe: str | None
    pair: tuple[str, ...]
    either: Foo | Bar
    cb: Callable[..., Any]

    def find(self, key: str) -> Foo | None:
        return None
