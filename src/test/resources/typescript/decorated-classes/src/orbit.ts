import { Controller, Get, Inject, Injectable, Module } from "@orbit/common";
import { OrbitStrategy, OrbitGuardFactory } from "@orbit/passport";
import { Strategy } from "orbit-jwt";
import { ORBIT_TOKEN, OrbitStore } from "./store";

@Injectable()
export class OrbitJwtStrategy extends OrbitStrategy(Strategy) {
  constructor(
    private readonly store: OrbitStore,
    @Inject(ORBIT_TOKEN) private readonly token: string,
  ) {
    super({ secret: token });
  }

  validate(payload: string): string {
    return this.store.read() + payload;
  }
}

@Injectable()
export class OrbitJwtGuard extends OrbitGuardFactory("jwt") {
  constructor(private readonly store: OrbitStore) {
    super();
  }

  allow(): boolean {
    return this.store.read() !== "";
  }
}

@Controller("orbits")
export class OrbitController {
  constructor(private readonly store: OrbitStore) {}

  @Get()
  list(): string {
    return this.store.read();
  }
}

@Module({
  imports: [OrbitStore],
  providers: [OrbitJwtStrategy, OrbitJwtGuard],
  controllers: [OrbitController],
})
export class OrbitModule {}
