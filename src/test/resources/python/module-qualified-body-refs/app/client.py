import pkg.models
import pkg.models as m
from pkg import models
import requests


class Client:
    def dotted_call(self):
        return pkg.models.User()

    def module_call(self):
        return models.User()

    def alias_call(self):
        return m.User()

    def class_attribute(self):
        return pkg.models.User.LIMIT

    def type_check(self, value):
        return isinstance(value, models.User)

    def annotated(self, user: models.User) -> None:
        pass

    def shadowed_by_local(self, other):
        models = other
        return models.User()

    def shadowed_by_loop(self, others):
        for m in others:
            m.User()

    def shadowed_by_param(self, models):
        return models.User()

    def through_instance(self):
        return self.models.User()

    def unknown_member(self):
        return models.Missing()

    def module_function(self):
        return models.make_user()

    def third_party(self):
        return requests.get("x")


class Derived(models.Base):
    pass


def top_level():
    return m.User.LIMIT
