from .base import Base
from uuid import UUID


class Child(Base):
    id: UUID
    parent: Base


def load(value: UUID, parent: Base) -> Base:
    return parent
