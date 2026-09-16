type Ctor<T> = new (...args: any[]) => T;

/** Produces a class carrying the members of the object literal it is given. */
export function fields<T>(shape: T): Ctor<T> {
  return class {} as any;
}

/** Produces a class carrying the members of its type argument. */
export function tag<Shape>(name: string): Ctor<Shape> {
  return class {} as any;
}
