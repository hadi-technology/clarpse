from .types import Foo as LocalFoo
from uuid import UUID

DEFAULT_FOO: LocalFoo
counter = 1


def build(foo: LocalFoo) -> LocalFoo:
    return foo


def parse_id(value: UUID) -> UUID:
    return value
