// Deeply nested recursive type that would cause stack overflow without depth limits
type DeepRecursive<T> = {
  value: T;
  next: DeepRecursive<T>;
};

// Mutual recursion between types
type NodeA = {
  b: NodeB | null;
};

type NodeB = {
  a: NodeA | null;
};

// Deeply nested generics
type Level1<T> = T;
type Level2<T> = Level1<Level1<T>>;
type Level3<T> = Level2<Level2<T>>;
type Level4<T> = Level3<Level3<T>>;
type Level5<T> = Level4<Level4<T>>;
type Level6<T> = Level5<Level5<T>>;
type Level7<T> = Level6<Level6<T>>;
type Level8<T> = Level7<Level7<T>>;

// Class using recursive types
class TreeNode {
  value: string;
  left: DeepRecursive<TreeNode> | null;
  right: DeepRecursive<TreeNode> | null;

  constructor(value: string) {
    this.value = value;
    this.left = null;
    this.right = null;
  }

  getLeft(): NodeA | null {
    return null;
  }
}

// Interface with deeply nested type parameter
interface Container<T> {
  data: T;
  wrap(): Container<Container<T>>;
}

// Function using deeply recursive types
function processNode(node: DeepRecursive<string>): string {
  return node.value;
}
