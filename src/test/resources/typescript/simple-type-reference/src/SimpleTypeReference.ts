import { ClassB } from "./Other";

export class Foo {
  static create(): Foo {
    return new Foo();
  }
}

export class Test {
  fieldVar: string;
  importedField: ClassB;

  constructor() {}

  method(s1: string, s2: number): void {
    const localVar: string = "x";
    new Foo();
  }

  testStatic(): void {
    Foo.create();
  }
}

export class SelfRef {
  constructor() {}
}
