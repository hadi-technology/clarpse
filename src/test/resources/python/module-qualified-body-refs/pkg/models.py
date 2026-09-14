class User:
    LIMIT = 10

    def __init__(self):
        self.name = "x"


class Base:
    pass


def make_user():
    return User()
