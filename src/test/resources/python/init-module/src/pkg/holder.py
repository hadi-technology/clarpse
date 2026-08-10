from src.pkg import Root


class Holder:
    root: Root

    def make(self) -> Root:
        return Root()
