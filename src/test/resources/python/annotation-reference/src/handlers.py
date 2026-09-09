class Blueprint:
    def route(self, path):
        def deco(fn):
            return fn
        return deco


app = Blueprint()


def register(fn):
    return fn


@register
def handler():
    return None


@app.route("/x")
def routed():
    return None
