import models as model_types


class Service:
    current: model_types.User

    def set_current(self, user: model_types.User) -> model_types.User:
        return user
