import typing

if typing.TYPE_CHECKING:
    from .types import User


class Service:
    user: User
