from typing import Literal


class LiteralTypes:
    def text(self) -> Literal["ready"]:
        return "ready"

    def count(self) -> Literal[1]:
        return 1

    def flag(self) -> Literal[True]:
        return True
