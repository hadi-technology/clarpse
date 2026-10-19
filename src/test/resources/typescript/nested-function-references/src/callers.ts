import {
  viaBindingArrow,
  viaBindingInitializer,
  viaCallback,
  viaDoublyNested,
  viaFunctionExpression,
  viaLocalArrow,
} from "./targets";

export function component(items: number[]): number {
  const handler = () => {
    return viaLocalArrow();
  };
  const doubled = items.map((n) => n + viaCallback());
  return handler() + doubled.length + doubly();
}

function doubly(): number {
  return [1].map(() => [2].map(() => viaDoublyNested()).length).length;
}

export class Widget {
  run(): void {
    setTimeout(function () {
      viaFunctionExpression();
    });
  }
}

export const arrowBinding = (): number => viaBindingArrow();

export const initialised = viaBindingInitializer();

export const plain = "a string";
