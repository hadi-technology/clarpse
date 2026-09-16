import { fields, tag } from "./mixins";

export class User extends fields({ id: "", email: "" }) {}

export class Repo extends tag<{ readonly find: (id: string) => string }>("Repo") {}

export class Named extends fields({ label: "" }) {
  describe(): string {
    return this.label;
  }
}

export interface Plugin {
  init?(): void;
  run(): void;
}
