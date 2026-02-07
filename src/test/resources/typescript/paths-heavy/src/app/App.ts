import { Profile, User } from "@domain";
import type { Id } from "@shared";
import { formatDate } from "@util";

export class App {
  run(user: User, profile: Profile, id: Id): string {
    return formatDate(user.label() + profile.name + id.value);
  }
}
