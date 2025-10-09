// Interface sample with explicit visibility via export
export interface IRepository<T, ID> {
  findById(id: ID): T;
  save(entity: T): void;
}

export interface INamed {
  getName(): string;
}
