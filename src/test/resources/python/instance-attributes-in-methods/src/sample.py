from .types import Item


class Estimator:
    def __init__(self, flag):
        self.flag = flag
        if flag:
            self.in_if = 1
        else:
            self.in_else = 2
        for i in range(3):
            self.in_for = i
        with open("x") as handle:
            self.in_with = handle
        try:
            self.in_try = 1
        except ValueError:
            self.in_except = 2
        finally:
            self.in_finally = 3
        (self.pair_a, self.pair_b) = (1, 2)
        self.bare_a, self.bare_b = 3, 4
        [self.list_a, self.list_b] = [5, 6]

    def fit(self, data):
        self.coef_ = data
        self.coef_ = data
        self.typed_: Item = Item()
        if data:
            self.fitted_in_if = True
        local = 1
        return local

    @classmethod
    def build(cls):
        cls.registry = {}

    def nested(self):
        def inner(other):
            other.not_a_field = 1
        self.with_inner = inner
