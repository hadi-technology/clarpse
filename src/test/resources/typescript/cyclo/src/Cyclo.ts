export class Test {
  constructor() {
    if (2 > 4 || (5 < 7 && 5 < 7)) {
      return;
    } else {
      while (true) {
        for (const s of ["a"]) {
          break;
        }
        break;
      }
    }
  }

  switcher(value: string): void {
    switch (value) {
      case "a":
        break;
      case "b":
        break;
    }
  }

  complex(): boolean {
    while (2 > 4) {
      for (let i = 0; i < 3 && 2 === 3; i++) {
        if (i === 3) {
          try {
            return false;
          } catch (e) {}
        }
      }
    }
    return true;
  }

  withComment(): boolean {
    /** test comment && || */
    while (2 > 4) {}
    return true;
  }
}

export interface ITest {
  aMethod(): boolean;
}

export class ClassCyclo {
  aMethod(): boolean {
    while (2 > 4) {
      for (let i = 0; i < 3 && 2 === 3; i++) {
        if (i === 3) {
          try {
            return false;
          } catch (e) {}
        }
      }
    }
    return true;
  }

  bMethod(): boolean {
    if (2 > 4 && 5 < 7) {
      for (const s of ["a"]) {
        if (s) {
          break;
        }
      }
    }
    return true;
  }
}

export class EmptyClass {
  tester = "test";
}
