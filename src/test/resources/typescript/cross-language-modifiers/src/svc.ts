export abstract class Svc {
  private static readonly cap: number = 3;
  protected abstract run(): void;
  public size(): number {
    return 0;
  }
  get label(): string {
    return "";
  }
}

export interface Shape {
  area(): number;
}

export enum Color {
  RED,
  GREEN
}

class Internal {
}

export const LIMIT = 5;

export function helper(): void {
}
