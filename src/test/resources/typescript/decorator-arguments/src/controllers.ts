import { Bare, Chained, Entity, PREFIX, Route, Service, Verb } from "./decorators";

@Route("/orders")
export class Orders {
  @Route("/list", Verb.Post, Service)
  list(): void {}

  @Route("/a" + "/b", 12, -3, true, ["x", "y"])
  literals(): void {}

  @Route(PREFIX, `tpl`, `x${PREFIX}`, compute())
  computed(): void {}

  @Bare
  bare(): void {}

  @Route()
  empty(): void {}

  @Chained("a")("b")
  chained(): void {}

  @Route({ name: "orders", unique: true, verb: Verb.Get, items: [Service] })
  optioned: string = "";
}

@Entity({ table: "widgets", indexed: true })
export class Widget {
  @Route({ name: PREFIX })
  dynamic = 1;

  @Route({ ...{ a: 1 } })
  spread = 2;

  @Route({ a: 1 }, { b: 2 })
  several = 3;
}

function compute(): string {
  return "c";
}
