from .types import Team, User


class Service:
    def __init__(self, user: User, team: Team) -> None:
        self.owner = user
        self.team = team
        self.owner = user
        temporary = team


class Outer:
    class Inner:
        def __init__(self, user: User) -> None:
            self.inner_owner = user


class Annotated:
    tag: Team = Team()

    def __init__(self) -> None:
        self.owner: User = User()
