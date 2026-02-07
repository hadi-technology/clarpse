export class Test {
  fieldVar: string;

  method(str: string): void {}

  get value(): number {
    return 1;
  }

  set value(val: number) {}

  methodWithLocal(): void {
    class LocalClass {}
  }
}

export interface TestInterface {
  method(): void;
  fieldVar: string;
}

export enum TestEnum {
  A,
  B,
  C
}
