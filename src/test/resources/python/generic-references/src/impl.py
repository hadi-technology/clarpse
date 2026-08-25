from .types import Bar, Base, Dto, Foo, HttpRequest


class Impl(Base[HttpRequest], Dto[HttpRequest]):
    parameterised: Foo[Bar]
    raw: Bar

    def ret(self) -> Foo[Bar]:
        return None
