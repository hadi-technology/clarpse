from enums import Verb, Plain

LIMIT = 9


def register(*args, **kwargs):
    return lambda target: target


def compute():
    return "x"


@register("/orders")
def single():
    pass


@register("/a", "/b")
def positional():
    pass


@register(path="/x", unique=True, size=12, ratio=-1.5, verb=Verb.POST)
def keyword():
    pass


@register(tags=["a", "b"], pair=("c", "d"), joined="p" "q", plus="r" + "s", text=r"a\d", esc="t\tab")
def literals():
    pass


@register(kind=Plain, limit=Plain.LIMIT, plain=LIMIT, call=compute(), fmt=f"x{LIMIT}", missing=None)
def unresolved():
    pass


@register
def bare():
    pass


@register()
def empty():
    pass


@register("/chained")(compute)
def chained():
    pass


class Controller:
    @register("/list", methods=["GET"])
    def list(self):
        pass


@register(name="widget")
class Widget:
    pass
