import os
from pkg.models import Model

DEFAULT_PATH = os.getcwd()


def make():
    return Model()


def describe(model: Model) -> str:
    return str(model)
