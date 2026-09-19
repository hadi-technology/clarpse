from typing import List


def capfirst(x):
    return x[:1].upper() + x[1:]


def join_all(items: List[str], sep: str = ",") -> str:
    return sep.join(items)


def countdown(n):
    if n <= 0:
        return n
    return countdown(n - 1)


def redefined(a):
    return a


def redefined(a, b):
    return a + b
