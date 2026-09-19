import src.pkg as api
from src.pkg import Redirect, PublicBase, Starred, Deep, Own, redirect
from src.cyc import Loop


class View(PublicBase):
    target: Redirect

    def go(self) -> Redirect:
        return Redirect()

    def starred(self):
        return Starred()

    def deep(self) -> Deep:
        return Deep()

    def own(self) -> Own:
        return Own()

    def call(self):
        return redirect("/")

    def qualified(self):
        return api.Redirect()

    def loop(self) -> Loop:
        return Loop()
