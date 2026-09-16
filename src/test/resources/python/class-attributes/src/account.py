import functools


class Account:
    """An account."""

    kind = "standard"
    limit: int = 10
    _private = 1

    @property
    def balance(self) -> int:
        return self.limit

    @functools.cached_property
    def history(self):
        return []

    def close(self) -> None:
        return None


def load_account(path):
    return Account()
