export function Component(config: { selector: string }) {
  return (target: unknown) => target;
}

export function Injectable() {
  return (target: unknown, key: string, descriptor: PropertyDescriptor) => descriptor;
}

export function Input() {
  return (target: unknown, key: string) => {};
}

@Component({ selector: "app-widget" })
export class Widget {
  @Input()
  label: string = "";

  @Injectable()
  render(): void {}
}

export class Plain {
  run(): void {}
}
