import enum
from enum import Enum


class Mode(Enum):
    FAST = 1
    SLOW = 2


class Level(enum.IntEnum):
    LOW = 1
    HIGH = 2


class Plain:
    kind = "standard"
