import { AlphaPart, BASE_SIZE, BetaPart, GAP, GammaPart, Mode, makeDelta } from "./parts";
import * as parts from "./parts";

export const partList = [AlphaPart, BetaPart];

export const partTable = {
  gamma: GammaPart,
  delta: makeDelta,
  mode: Mode,
};

export const shorthandTable = { AlphaPart };

export const TOTAL = BASE_SIZE + GAP;

export const viaNamespace = [parts.GammaPart];

export const localsOnly = ((AlphaPart: number) => [AlphaPart])(1);

export const literalsOnly = { name: "plain", count: 3, items: [1, 2, 3] };

export const fromType = 5 as number;

export class Holder {
  readonly members = [BetaPart];
  readonly size = BASE_SIZE;
}
