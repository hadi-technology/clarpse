import { Base } from "./Base";
import { Repo } from "./Repo";

/** Service doc */
export class Service extends Base implements Repo {
  method(param: string): void {}
}
