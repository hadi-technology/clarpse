from typing import Generic, TypeVar

T = TypeVar("T")


class Base(Generic[T]):
    pass


class Arg:
    pass


class Inner:
    pass


class Wrapper(Generic[T]):
    pass


class Derived(Base[Arg]):
    pass


class Nested(Base[Wrapper[Inner]]):
    pass
