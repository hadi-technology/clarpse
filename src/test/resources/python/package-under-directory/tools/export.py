import app.store.base


class Exporter:
    def __init__(self):
        self.store = app.store.base.FileStore()
