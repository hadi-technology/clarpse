class User:
    first: str
    last: str

class Address:
    city: str

def build_name(first: str, last: str) -> str:
    return first + " " + last
