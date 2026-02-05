export class Foo {}
export class Bar {}

export class Test {
  m(): void {
    const a = new Foo(), b = new Bar();
  }
}
