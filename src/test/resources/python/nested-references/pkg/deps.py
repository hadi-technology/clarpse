class Db:
    pass


class Cache:
    pass


class Base:
    pass


class Mixin:
    pass


class Marker:
    pass


def get_db():
    return Db()


def Depends(dependency):
    return dependency


def tagged(kind):
    def wrap(target):
        return target
    return wrap
