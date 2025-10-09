<?php

// Interface sample with explicit visibility on methods
interface IRepository {
    public function findById($id);
    public function save($entity): void;
}

interface INamed {
    public function getName(): string;
}
