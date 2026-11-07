from .shapes import Either, Shape, StateManager


class Canvas:
    def draw(self, shape: Shape) -> None:
        pass

    def pick(self, choice: Either) -> None:
        pass

    def maybe(self, shape: Shape | None) -> None:
        pass

    def manage(self, manager: StateManager) -> None:
        pass
