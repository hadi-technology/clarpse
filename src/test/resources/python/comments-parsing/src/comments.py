class Test:
    """Class doc for Test."""

    field_var: str

    def test(self, method_param: str) -> str:
        """method doc for test."""
        return method_param


class NoComment:
    pass


class Factory:
    """Factory class doc."""

    @classmethod
    def build(cls, name: str) -> None:
        """classmethod doc for build."""
        _ = name
        return None
