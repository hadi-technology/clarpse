export class ParameterizedTestIntegrationTests {
  values: Array<string>;

  constructor(values: Array<string>) {
    this.values = values;
  }

  run(testName: string, count: number): boolean {
    return count > 0;
  }
}
