def helper():
    return 1


def other():
    return 2


def unrelated():
    return 3


def main():
    return helper()


def shadowed(helper):
    return helper()


def recursive(n):
    return recursive(n - 1)


_PARSERS = (helper, other)
_NAMED = {"a": other}
_PLAIN = (1, 2)


def run(value):
    for parse in _PARSERS:
        parse(value)


class Runner:
    def go(self):
        return helper()
