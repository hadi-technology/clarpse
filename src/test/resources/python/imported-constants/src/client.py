from .errors import CODES, LABEL, make_error


class Client:
    def send(self, code):
        return code in CODES

    def label(self):
        return LABEL

    def shadowed(self, CODES):
        return CODES

    def makes(self):
        return make_error()


def check(code):
    return code in CODES


def local_binding():
    LABEL = "local"
    return LABEL
