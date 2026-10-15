import { Store } from "../lib/store";
import * as path from "path";

export function label(store: Store): string {
  return path.join("store", String(store.size));
}

export const DEFAULT_LABEL = "store";

export type Labeller = (store: Store) => string;
