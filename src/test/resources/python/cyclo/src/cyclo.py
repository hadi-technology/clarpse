class Metrics:
    def __init__(self, value: int) -> None:
        if value > 0 and value < 10:
            self.value = value
        else:
            self.value = 0

    def evaluate(self, limit: int) -> int:
        if limit > 10:
            return limit
        elif limit > 5:
            return limit - 1
        return 0


def compute(total: int) -> int:
    while total > 0 and total < 3:
        total += 1
    return total
