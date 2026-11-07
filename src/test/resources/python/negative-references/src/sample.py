LIMIT = 1


class Widget:
    pass


INSTANCE = Widget()
COPY = INSTANCE


def helper():
    return 1


def local_assignment():
    helper = 2
    return helper


def comprehension(items):
    return [helper for helper in items]


def nested_def():
    def helper():
        return 3
    return helper()


def nested_param():
    def inner(helper):
        return helper
    return inner


def local_import():
    from .errors import LIMIT
    return LIMIT


def local_import_constant():
    from .errors import CODES
    return CODES
