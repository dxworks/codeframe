<?php

namespace App\Closures;

// File-level constants
const MULTIPLIER = 2;
const DEFAULT_PREFIX = 'Item';

// File-level method call
set_error_handler(function($errno, $errstr) {
    error_log("Error [{$errno}]: {$errstr}");
    return true;
});

/**
 * Sample demonstrating PHP closures and arrow functions for CodeFrame analysis.
 * Closures are anonymous functions that can capture variables from the enclosing scope.
 */

// ---------------------------
// Basic Closure
// ---------------------------

$greet = function(string $name): string {
    return "Hello, {$name}!";
};

// Closure with use keyword (capturing by value)
$prefix = 'Mr.';
$formalGreet = function(string $name) use ($prefix): string {
    return "Hello, {$prefix} {$name}!";
};

// Closure with use keyword (capturing by reference)
$counter = 0;
$increment = function() use (&$counter): int {
    return ++$counter;
};

// ---------------------------
// Arrow Functions (PHP 7.4+)
// ---------------------------

$double = fn(int $x): int => $x * 2;
$square = fn(int $x): int => $x * $x;
$add = fn(int $a, int $b): int => $a + $b;

// Arrow function with implicit capture
$factor = 10;
$multiply = fn(int $x): int => $x * $factor;

// Arrow function returning closure
$createMultiplier = fn(int $factor): callable => fn(int $x): int => $x * $factor;

// ---------------------------
// Class with Closure Properties and Methods
// ---------------------------

class Calculator
{
    private array $operations = [];
    private ?callable $logger = null;
    
    public function __construct(?callable $logger = null)
    {
        $this->logger = $logger;
        $this->initializeOperations();
    }
    
    private function initializeOperations(): void
    {
        $this->operations = [
            'add' => fn(float $a, float $b): float => $a + $b,
            'subtract' => fn(float $a, float $b): float => $a - $b,
            'multiply' => fn(float $a, float $b): float => $a * $b,
            'divide' => function(float $a, float $b): float {
                if ($b === 0.0) {
                    throw new \InvalidArgumentException('Division by zero');
                }
                return $a / $b;
            },
        ];
    }
    
    public function registerOperation(string $name, callable $operation): void
    {
        $this->operations[$name] = $operation;
    }
    
    public function execute(string $operation, float $a, float $b): float
    {
        if (!isset($this->operations[$operation])) {
            throw new \InvalidArgumentException("Unknown operation: {$operation}");
        }
        
        $result = $this->operations[$operation]($a, $b);
        
        if ($this->logger !== null) {
            ($this->logger)("{$a} {$operation} {$b} = {$result}");
        }
        
        return $result;
    }
    
    public function setLogger(callable $logger): void
    {
        $this->logger = $logger;
    }
    
    public function getOperationNames(): array
    {
        return array_keys($this->operations);
    }
}

// ---------------------------
// Class Using Closures for Callbacks
// ---------------------------

class Collection
{
    private array $items;
    
    public function __construct(array $items = [])
    {
        $this->items = $items;
    }
    
    public function map(callable $callback): self
    {
        $mapped = array_map($callback, $this->items);
        return new self($mapped);
    }
    
    public function filter(callable $predicate): self
    {
        $filtered = array_filter($this->items, $predicate);
        return new self(array_values($filtered));
    }
    
    public function reduce(callable $callback, mixed $initial = null): mixed
    {
        return array_reduce($this->items, $callback, $initial);
    }
    
    public function each(callable $callback): void
    {
        foreach ($this->items as $key => $item) {
            $callback($item, $key);
        }
    }
    
    public function find(callable $predicate): mixed
    {
        foreach ($this->items as $item) {
            if ($predicate($item)) {
                return $item;
            }
        }
        return null;
    }
    
    public function sort(callable $comparator): self
    {
        $sorted = $this->items;
        usort($sorted, $comparator);
        return new self($sorted);
    }
    
    public function toArray(): array
    {
        return $this->items;
    }
    
    public function count(): int
    {
        return count($this->items);
    }
}

// ---------------------------
// Class with Closure Binding
// ---------------------------

class EventEmitter
{
    private array $listeners = [];
    
    public function on(string $event, callable $listener): void
    {
        if (!isset($this->listeners[$event])) {
            $this->listeners[$event] = [];
        }
        $this->listeners[$event][] = $listener;
    }
    
    public function emit(string $event, mixed ...$args): void
    {
        if (!isset($this->listeners[$event])) {
            return;
        }
        
        foreach ($this->listeners[$event] as $listener) {
            $listener(...$args);
        }
    }
    
    public function once(string $event, callable $listener): void
    {
        $wrapper = function(...$args) use ($event, $listener, &$wrapper): void {
            $this->off($event, $wrapper);
            $listener(...$args);
        };
        $this->on($event, $wrapper);
    }
    
    public function off(string $event, callable $listener): void
    {
        if (!isset($this->listeners[$event])) {
            return;
        }
        
        $this->listeners[$event] = array_filter(
            $this->listeners[$event],
            fn($l) => $l !== $listener
        );
    }
    
    public function removeAllListeners(string $event): void
    {
        unset($this->listeners[$event]);
    }
}

// ---------------------------
// Higher-Order Functions
// ---------------------------

class FunctionUtils
{
    public static function compose(callable ...$functions): callable
    {
        return function($value) use ($functions) {
            return array_reduce(
                array_reverse($functions),
                fn($carry, $fn) => $fn($carry),
                $value
            );
        };
    }
    
    public static function pipe(callable ...$functions): callable
    {
        return function($value) use ($functions) {
            return array_reduce(
                $functions,
                fn($carry, $fn) => $fn($carry),
                $value
            );
        };
    }
    
    public static function curry(callable $fn, int $arity): callable
    {
        return function(...$args) use ($fn, $arity) {
            if (count($args) >= $arity) {
                return $fn(...$args);
            }
            return self::curry(
                fn(...$more) => $fn(...$args, ...$more),
                $arity - count($args)
            );
        };
    }
    
    public static function memoize(callable $fn): callable
    {
        $cache = [];
        return function(...$args) use ($fn, &$cache) {
            $key = serialize($args);
            if (!isset($cache[$key])) {
                $cache[$key] = $fn(...$args);
            }
            return $cache[$key];
        };
    }
    
    public static function debounce(callable $fn, int $delay): callable
    {
        $lastCall = 0;
        return function(...$args) use ($fn, $delay, &$lastCall) {
            $now = microtime(true) * 1000;
            if ($now - $lastCall >= $delay) {
                $lastCall = $now;
                return $fn(...$args);
            }
            return null;
        };
    }
}

// ---------------------------
// Standalone functions using closures
// ---------------------------

function createCounter(int $start = 0): callable
{
    $count = $start;
    return function() use (&$count): int {
        return $count++;
    };
}

function createFormatter(string $prefix, string $suffix = ''): callable
{
    return fn(string $value): string => "{$prefix}{$value}{$suffix}";
}

function applyToAll(array $items, callable $fn): array
{
    return array_map($fn, $items);
}

function filterBy(array $items, callable $predicate): array
{
    return array_values(array_filter($items, $predicate));
}
