export class Test {
  fieldVar: string;

  aMethod(): string {
    return "";
  }

  methodWithLocal(): void {
    const localVar: string = "";
  }

  methodWithParam(param: string): void {}
}

export interface ITest {
  fieldVar: string;
  aMethod(): string;
  methodWithParam(param: string): void;
}
