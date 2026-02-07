export class SampleClass {
  sampleClassField: string;

  constructor(sampleConstructorParam: string) {}

  sampleMethod(sampleMethodParam: string, sampleMethodParam2: object): string {
    return "";
  }
}

export interface SampleInterface {
  sampleInterfaceMethod(sampleInterfaceMethodParam: string): void;
}

export enum SampleEnum {
  SampleEnumConstant = "const"
}

export function topLevelFunction(topLevelParam: string): number {
  return topLevelParam.length;
}
