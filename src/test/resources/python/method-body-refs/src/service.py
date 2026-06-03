from src.types import User, Address, build_name
from src.dao import UserDao


class UserService:
    def get_user(self, user_id: int) -> User:
        dao = UserDao()
        user = dao.find_by_id(user_id)
        addr = Address(user_id)
        build_name(user.first, user.last)
        return user

    def process(self, data: str) -> str:
        result = data.strip()
        return result
