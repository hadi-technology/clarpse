import { Store, audit, describeStore } from "./store";

/** What a client is built from. */
export type Options = { store: Store; retries: number };

export type Handler<T> = (input: T) => Promise<Store>;

export type Mode = "fast" | "slow";

type Internal = { mode: Mode };

export function buildStore(options: Options): Store {
  return new Store(options.retries);
}

export const createClient = (options: Options) => {
  const store = buildStore(options);
  return describeStore(store);
};

export const serve = function (store: Store): number {
  return audit(store);
};

export const LIMIT = 10;
