/** Widget doc */
export class Widget {
  /** field doc */
  readonly id: string;

  constructor(id: string) {
    this.id = id;
  }

  /** method doc */
  compute(flag: boolean): number {
    let total = 0;
    if (flag && this.id) {
      total += 1;
    }
    for (let i = 0; i < 2; i++) {
      total += i;
    }
    return total;
  }
}
