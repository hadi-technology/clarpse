from typing import Generic, TypeVar

from . import archive as arch
from .archive import Item, Store

T = TypeVar("T")


class Base(Generic[T]):
    pass


class Direct(Base[Item]):
    pass


class Dotted(Base[arch.Item]):
    pass


class Mapped(Store[str, Item]):
    pass
