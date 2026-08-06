class Svc:
    owner: str

    def size(self) -> int:
        return 0

    def _helper(self) -> int:
        return 1

    def __secret(self) -> int:
        return 2

    def __str__(self) -> str:
        return "Svc"

    @staticmethod
    def make() -> int:
        return 3

    @classmethod
    def of(cls) -> int:
        return 4


class _Hidden:
    pass
