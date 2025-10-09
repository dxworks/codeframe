// Parameter order test: required, optional, rest (valid ordering)
export function f(a: number, b?: string, ...rest: any[]): void {}

export class C {
    m(x: string, y?: number, ...z: number[]): void {}
}
