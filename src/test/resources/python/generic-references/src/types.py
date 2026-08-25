from typing import Generic, TypeVar

T = TypeVar('T')


class Dto(Generic[T]):
    pass


class Base(Generic[T]):
    pass


class HttpRequest:
    pass


class Bar:
    pass


class Foo(Generic[T]):
    pass
