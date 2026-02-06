import type { Id } from "@shared";

export class User {
  constructor(public id: Id) {}

  label(): string {
    return this.id.value;
  }
}
