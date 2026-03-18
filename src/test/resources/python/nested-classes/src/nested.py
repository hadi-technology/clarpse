class Outer:
    class Inner:
        class Deep:
            def ping(self) -> int:
                return 1

        def build(self) -> None:
            return None

    def top(self) -> None:
        return None
