import { Store } from "./store";

export class Client {
  constructor(readonly store: Store) {}
}

export function connect(store: Store): Client {
  return new Client(store);
}
