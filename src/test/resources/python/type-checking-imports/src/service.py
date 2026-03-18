from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .types import User


class Service:
    user: User

    def set_user(self, user: User) -> User:
        return user
