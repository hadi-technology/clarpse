from .types import (
    Foo,
    Bar as AliasBar,
)


class Example(Foo):
    first: Foo
    second: AliasBar
