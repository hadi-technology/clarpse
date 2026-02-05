/** Class doc */
export class Test {
  /** field doc */
  fieldVar: string;

  /**
   * method doc
   */
  test(/** param doc */ methodParam: string): void {}
}

export class NoComment {}

/** Interface doc */
export interface TestInterface {
  /** interface method doc */
  method(): void;
}

/** Enum doc */
export enum TestEnum {
  A
}
