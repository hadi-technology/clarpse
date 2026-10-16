export class Store {
  constructor(readonly size: number) {}
}

export function audit(store: Store): number {
  return store.size;
}

export const describeStore = (store: Store): string => `store of ${store.size}`;
