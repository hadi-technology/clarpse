export const TOKENS = ['alpha', 'beta', 'gamma'] as const;
export type Token = (typeof TOKENS)[number];
export type Id = 'one' | 'two';

export interface Flags {
  enabled: boolean;
}

export function tokensFromFlags(flags: Partial<Flags>): Token[] {
  return [...TOKENS];
}

export function inferredTokens(flags: Flags) {
  return [...TOKENS];
}

export function literalReturn(): 'a' | 'b' {
  return 'a';
}

export const RUNTIME: Record<Id, readonly Token[]> = {
  one: [],
  two: []
};

export const inferredConst = TOKENS;

export class Holder {
  items: Token[] = [];

  multiLine: Record<
    Id,
    readonly Token[]
  > = { one: [], two: [] };

  inferredField = TOKENS;

  describe(flags: Flags): Token[] {
    return this.items;
  }
}

export interface Shape {
  tokens: Token[];
  byId: Record<Id, readonly Token[]>;
}

export function multiLineReturn(): Promise<
  Record<Id, Token[]>
> {
  return Promise.resolve({ one: [], two: [] });
}
