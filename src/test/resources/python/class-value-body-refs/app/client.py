import pkg.models as m
from pkg.models import User, Admin, make_user, DEFAULT_NAME


def register(kind):
    return kind


class Client:
    def class_method_call(self):
        return User.create()

    def class_attribute(self):
        return User.LIMIT

    def alias_class_method_call(self):
        return m.User.create()

    def instance_check(self, value):
        return isinstance(value, User)

    def subclass_check(self, kind):
        return issubclass(kind, User)

    def passed_as_argument(self):
        return register(User)

    def assigned(self):
        handler = User
        return handler

    def in_a_tuple(self, value):
        return isinstance(value, (User, Admin))

    def same_module_class(self):
        return register(Helper)

    def shadowed_by_local(self, factory):
        User = factory
        return isinstance(factory, User)

    def shadowed_by_param(self, User):
        return register(User)

    def through_instance(self):
        return self.User

    def function_value(self):
        return register(make_user)

    def constant_value(self):
        return DEFAULT_NAME

    def keyword_named_like_a_class(self):
        return dict(User=1)


def module_level_check(value):
    return isinstance(value, Admin)


class Helper:
    pass
