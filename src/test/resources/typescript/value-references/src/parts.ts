export class AlphaPart {
  id = 1;
}

export class BetaPart {
  id = 2;
}

export class GammaPart {
  id = 3;
}

export function makeDelta(): number {
  return 4;
}

export enum Mode {
  Fast,
  Slow,
}

export const BASE_SIZE = 10;
export const GAP = 2;
export const UNUSED = 99;

export interface Shape {
  size: number;
}
