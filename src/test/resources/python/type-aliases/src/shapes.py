from typing import Optional, Union


class Circle:
    pass


class Square:
    pass


class LocalStateManager:
    pass


Shape = Circle | Square
MaybeShape = Optional[Circle]
Either = Union[Circle, Square]
StateManager = LocalStateManager
LIMIT = 10
PRINTER = print
