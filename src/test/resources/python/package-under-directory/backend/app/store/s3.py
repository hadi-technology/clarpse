from app.store.base import FileStore


class S3Store(FileStore):
    def read(self, name):
        return name
