from abc import ABCMeta

from .base import Base, Mixin


class Plain(Base, Mixin):
    pass


class WithMetaclass(Base, metaclass=ABCMeta):
    pass


class WithOtherKeyword(Mixin, flag=Base):
    pass
