import { User } from "@lib";
import { Base } from "./base";

export interface Repo {
  get(): User;
}

export class Service extends Base implements Repo {
  constructor(private user: User, id: string) {
    super(id);
  }

  get(): User {
    return this.user;
  }
}
