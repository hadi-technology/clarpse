export enum Verb {
  Get,
  Post,
}

export class Service {}

export const PREFIX = "/api";

export function Route(...args: unknown[]): any {
  return (target: unknown, key?: string) => undefined;
}

export function Entity(...args: unknown[]): any {
  return (target: unknown) => undefined;
}

export function Bare(target: unknown, key?: string): void {}

export function Chained(...args: unknown[]): any {
  return (...more: unknown[]) => (target: unknown) => undefined;
}
