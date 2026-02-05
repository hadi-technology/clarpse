/** Access modifier fixture */
export class Test {}

export interface TestInterface {}

export enum TestEnum {
  A,
  B
}

class Tester {
  public static fieldVar: number;

  private static lolcakes(): boolean {
    return true;
  }

  method(): void {
    const localConst = "x";
    var localVar = "y";
  }
}

class PrivateCtor {
  private constructor() {}
}

class ParamTest {
  constructor(public readonly str: string) {}
}

abstract class AbstractTester {
  abstract doWork(): boolean;
}
