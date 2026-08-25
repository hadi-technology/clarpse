import { Bar, Base, Dto, Foo, HttpRequest } from './types';

export class Impl extends Base<HttpRequest> implements Dto<HttpRequest> {
  parameterised: Foo<Bar>;
  raw: Bar;
  arrayOfParameterised: Foo<Bar>[];

  ret(): Foo<Bar> {
    return null as any;
  }
}
