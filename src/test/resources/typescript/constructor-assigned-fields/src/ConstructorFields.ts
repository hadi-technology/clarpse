export class User {}

export class Service {
  owner: User;

  constructor(owner: User) {
    this.owner = owner;
    const temporary = owner;
  }
}
