export interface Dto<T> {
  value: T;
}

export class Base<T> {
  held: T;
}

export class HttpRequest {
}

export class Bar {
}

export class Foo<T> {
  wrapped: T;
}
