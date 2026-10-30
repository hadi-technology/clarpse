import json
from pkg.models import Model


class Service:
    def run(self) -> Model:
        return Model()


def build():
    return Service()


SERVICE_NAME = json.dumps("service")
