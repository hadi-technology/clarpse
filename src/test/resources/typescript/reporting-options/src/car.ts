import { Engine } from "./engine";

export class Car {
  constructor(private readonly engine: Engine) {}

  start(): void {
    this.engine.ignite();
  }
}
