interface List {}
interface Map<K, V> {}

class GenericTest<T> {}
class GenericTest2<T extends List> {}

class FieldTest {
  fieldVar: List;
  x: List;
  complexField: Map<string, List>;
}

interface InterfaceTest {
  sMethod(): Map<string, List>;
}

class MethodTest {
  sMethod(): Map<string, List> {
    return null as any;
  }

  complexMethod(s: string, t: number): Map<List, string[]> {
    return null as any;
  }
}
