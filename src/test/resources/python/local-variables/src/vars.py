from .types import Helper


def build(count: int) -> Helper:
    total = 0
    label: str = "x"
    helper: Helper = Helper()
    total = count + 1
    for item in range(count):
        total += item
    with open("f") as handle:
        label = handle.name
    try:
        pass
    except ValueError as error:
        label = str(error)
    first, second = 1, 2
    if (found := count) > 0:
        total = found
    squares = [n for n in range(3)]
    return helper


class Service:
    def run(self) -> None:
        self.cached = 1
        value = 2

        def inner():
            hidden = 3
            return hidden

        return None
