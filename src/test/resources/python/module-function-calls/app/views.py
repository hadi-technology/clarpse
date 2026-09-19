import util.text
from util import text as t
from util.text import capfirst, join_all, redefined


class View:
    def title(self, s):
        return capfirst(s)

    def joined(self, items):
        return join_all(items)

    def qualified(self, s):
        return util.text.capfirst(s)

    def aliased(self, s):
        return t.capfirst(s)

    def ambiguous(self, s):
        return redefined(s, s)

    def unknown(self, s):
        return t.not_defined(s)


def headline(s):
    return capfirst(s)
