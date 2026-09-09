from dataclasses import dataclass


@dataclass
class User:
    name: str

    @property
    def label(self):
        return self.name


class Plain:
    def ordinary(self):
        return None
