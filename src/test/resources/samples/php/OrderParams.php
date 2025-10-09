<?php

// Parameter order test: simple parameters mixed with property promotion
class Svc {
    public function __construct(private int $id, string $name, protected Logger $logger) {}

    public function m(string $a, private int $b, $c) {}
}

function f(int $x, string $y, bool $z) {}
