export class Item {}

export class List {
  get(index: number): Item {
    return new Item();
  }
}

export class Test {
  getList(): List {
    return new List();
  }

  m(): void {
    this.getList().get(0);
  }
}
