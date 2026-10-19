<?php

// Interface sample with explicit visibility on methods
const FILE_LEVEL = 'file';

#[Contract]
interface IRepository {
    public const TIMEOUT = 30;
    const RETRIES = 3;

    public function findById($id);
    public function save($entity): void;
}

#[NamedContract]
interface INamed {
    public function getName(): string;
}
