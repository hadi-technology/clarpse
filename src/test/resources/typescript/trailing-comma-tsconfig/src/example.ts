export class Example {
    private name: string;

    constructor(name: string) {
        this.name = name;
    }

    public greet(): string {
        return `Hello, ${this.name}!`;
    }
}
