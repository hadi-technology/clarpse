class User:
    LIMIT = 10

    @classmethod
    def create(cls):
        return cls()


class Admin(User):
    pass


def make_user():
    return User()


DEFAULT_NAME = "x"
