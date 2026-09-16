import { helper } from "./a";

export function useIt(): number {
  return helper();
}

export function useItTwice(): number {
  return helper() + helper();
}
