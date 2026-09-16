export function helper(): number {
  return 1;
}

export function localCaller(): number {
  return helper();
}
