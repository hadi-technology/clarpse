from typing import Dict, List, Optional

from .types import Group, User


class Example:
    user: Optional[User]
    users: List[User]
    mapping: Dict[str, Group]
