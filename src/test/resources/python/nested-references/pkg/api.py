from pkg.deps import Base, Cache, Db, Depends, Marker, Mixin, get_db, tagged


def handler(db=Depends(get_db)):
    return db


def outer():
    def inner():
        return Db()

    class Local(Base):
        pass

    return inner, Local


def deep():
    def first():
        def second():
            return Cache()
        return second
    return first


@tagged(Marker)
def decorated():
    return None


class Service:
    def method(self):
        def helper():
            return Mixin()
        return helper

    def with_default(self, db=Depends(get_db)):
        return db
