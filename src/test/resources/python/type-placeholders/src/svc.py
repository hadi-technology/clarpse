from typing import Any, List, Optional

import requests

from .helper import Helper


class Svc:
    items: List[str]
    anything: Any

    def __init__(self, helper: Helper):
        self.helper = helper

    def typed(self, n: int) -> Helper:
        return self.helper

    def untyped(self, n):
        return n

    def returns_none(self) -> None:
        return None

    def external(self) -> requests.Response:
        return requests.get("x")

    def optional(self) -> Optional[Helper]:
        return None
