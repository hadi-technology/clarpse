export class Base<T> {
  value?: T;
}

export interface Handler<T> {
  handle(input: T): void;
}

export class Arg {}

export class Inner {}

export class Wrapper<T> {
  held?: T;
}

export class Derived extends Base<Arg> {}

export class Nested extends Base<Wrapper<Inner>> {}

export class Impl implements Handler<Arg> {
  handle(input: Arg): void {}
}

export interface Child extends Handler<Arg> {}

export class Mixed extends Base<string> implements Handler<Arg> {
  handle(input: Arg): void {}
}
