from .types import User


class Service:
    owner: User

    def __init__(self, user: User) -> None:
        self.owner = user

    def update(self, user: User) -> User:
        return user
