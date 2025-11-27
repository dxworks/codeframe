<?php

namespace App\Magic;

// File-level constant
const MAGIC_VERSION = '1.0.0';

/**
 * Sample demonstrating PHP magic methods for CodeFrame analysis.
 * Magic methods are special methods that override PHP's default behavior.
 */

// ---------------------------
// Basic Magic Methods
// ---------------------------

class Entity
{
    private array $data = [];
    private array $modified = [];
    
    // Constructor
    public function __construct(array $data = [])
    {
        $this->data = $data;
    }
    
    // Destructor
    public function __destruct()
    {
        // Cleanup logic
        $this->data = [];
    }
    
    // Property getter
    public function __get(string $name): mixed
    {
        if (array_key_exists($name, $this->data)) {
            return $this->data[$name];
        }
        throw new \InvalidArgumentException("Property {$name} does not exist");
    }
    
    // Property setter
    public function __set(string $name, mixed $value): void
    {
        $this->data[$name] = $value;
        $this->modified[$name] = true;
    }
    
    // Property existence check
    public function __isset(string $name): bool
    {
        return isset($this->data[$name]);
    }
    
    // Property unset
    public function __unset(string $name): void
    {
        unset($this->data[$name]);
        unset($this->modified[$name]);
    }
    
    // String representation
    public function __toString(): string
    {
        return json_encode($this->data);
    }
    
    // Debug info
    public function __debugInfo(): array
    {
        return [
            'data' => $this->data,
            'modified' => array_keys($this->modified),
        ];
    }
    
    public function getData(): array
    {
        return $this->data;
    }
    
    public function isModified(string $name): bool
    {
        return isset($this->modified[$name]);
    }
}

// ---------------------------
// Method Overloading
// ---------------------------

class MethodOverloader
{
    private array $methods = [];
    
    public function __construct()
    {
        $this->registerDefaultMethods();
    }
    
    private function registerDefaultMethods(): void
    {
        $this->methods['greet'] = function(string $name): string {
            return "Hello, {$name}!";
        };
        
        $this->methods['add'] = function(int $a, int $b): int {
            return $a + $b;
        };
    }
    
    // Dynamic method calls
    public function __call(string $name, array $arguments): mixed
    {
        if (isset($this->methods[$name])) {
            return call_user_func_array($this->methods[$name], $arguments);
        }
        throw new \BadMethodCallException("Method {$name} does not exist");
    }
    
    // Static method calls
    public static function __callStatic(string $name, array $arguments): mixed
    {
        if ($name === 'create') {
            return new self();
        }
        throw new \BadMethodCallException("Static method {$name} does not exist");
    }
    
    public function registerMethod(string $name, callable $callback): void
    {
        $this->methods[$name] = $callback;
    }
    
    public function hasMethod(string $name): bool
    {
        return isset($this->methods[$name]);
    }
}

// ---------------------------
// Invokable Class
// ---------------------------

class Validator
{
    private array $rules;
    
    public function __construct(array $rules)
    {
        $this->rules = $rules;
    }
    
    // Make object callable
    public function __invoke(array $data): array
    {
        $errors = [];
        
        foreach ($this->rules as $field => $rule) {
            if ($rule === 'required' && empty($data[$field])) {
                $errors[$field] = "{$field} is required";
            } elseif ($rule === 'email' && !filter_var($data[$field] ?? '', FILTER_VALIDATE_EMAIL)) {
                $errors[$field] = "{$field} must be a valid email";
            }
        }
        
        return $errors;
    }
    
    public function getRules(): array
    {
        return $this->rules;
    }
    
    public function addRule(string $field, string $rule): void
    {
        $this->rules[$field] = $rule;
    }
}

// ---------------------------
// Cloning and Serialization
// ---------------------------

class DeepCopyable
{
    private int $id;
    private string $name;
    private array $items;
    private ?\DateTime $createdAt;
    
    public function __construct(int $id, string $name)
    {
        $this->id = $id;
        $this->name = $name;
        $this->items = [];
        $this->createdAt = new \DateTime();
    }
    
    // Clone handler
    public function __clone(): void
    {
        // Deep copy mutable objects
        $this->createdAt = clone $this->createdAt;
        $this->items = array_map(function($item) {
            return is_object($item) ? clone $item : $item;
        }, $this->items);
    }
    
    // Serialize handler
    public function __serialize(): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'items' => $this->items,
            'createdAt' => $this->createdAt?->format('c'),
        ];
    }
    
    // Unserialize handler
    public function __unserialize(array $data): void
    {
        $this->id = $data['id'];
        $this->name = $data['name'];
        $this->items = $data['items'];
        $this->createdAt = $data['createdAt'] ? new \DateTime($data['createdAt']) : null;
    }
    
    // Legacy sleep (for serialize())
    public function __sleep(): array
    {
        return ['id', 'name', 'items'];
    }
    
    // Legacy wakeup (for unserialize())
    public function __wakeup(): void
    {
        $this->createdAt = new \DateTime();
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function addItem(mixed $item): void
    {
        $this->items[] = $item;
    }
    
    public function getItems(): array
    {
        return $this->items;
    }
}

// ---------------------------
// State Export/Import
// ---------------------------

class Exportable
{
    private int $id;
    private string $name;
    private array $attributes;
    
    public function __construct(int $id, string $name, array $attributes = [])
    {
        $this->id = $id;
        $this->name = $name;
        $this->attributes = $attributes;
    }
    
    // Export state for var_export()
    public static function __set_state(array $properties): self
    {
        return new self(
            $properties['id'],
            $properties['name'],
            $properties['attributes']
        );
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function getAttributes(): array
    {
        return $this->attributes;
    }
    
    public function setAttribute(string $key, mixed $value): void
    {
        $this->attributes[$key] = $value;
    }
}

// ---------------------------
// Array Access Implementation
// ---------------------------

class Collection implements \ArrayAccess, \Countable, \IteratorAggregate
{
    private array $items = [];
    
    public function __construct(array $items = [])
    {
        $this->items = $items;
    }
    
    // ArrayAccess implementation
    public function offsetExists(mixed $offset): bool
    {
        return isset($this->items[$offset]);
    }
    
    public function offsetGet(mixed $offset): mixed
    {
        return $this->items[$offset] ?? null;
    }
    
    public function offsetSet(mixed $offset, mixed $value): void
    {
        if ($offset === null) {
            $this->items[] = $value;
        } else {
            $this->items[$offset] = $value;
        }
    }
    
    public function offsetUnset(mixed $offset): void
    {
        unset($this->items[$offset]);
    }
    
    // Countable implementation
    public function count(): int
    {
        return count($this->items);
    }
    
    // IteratorAggregate implementation
    public function getIterator(): \Traversable
    {
        return new \ArrayIterator($this->items);
    }
    
    public function toArray(): array
    {
        return $this->items;
    }
    
    public function map(callable $callback): self
    {
        return new self(array_map($callback, $this->items));
    }
    
    public function filter(callable $predicate): self
    {
        return new self(array_filter($this->items, $predicate));
    }
}

// ---------------------------
// Fluent Interface with Magic Methods
// ---------------------------

class QueryBuilder
{
    private string $table = '';
    private array $selects = ['*'];
    private array $wheres = [];
    private array $orders = [];
    private ?int $limitValue = null;
    
    // Fluent method calls via __call
    public function __call(string $name, array $arguments): self
    {
        // Handle whereXxx methods
        if (str_starts_with($name, 'where')) {
            $field = lcfirst(substr($name, 5));
            $field = preg_replace('/([A-Z])/', '_$1', $field);
            $field = strtolower(ltrim($field, '_'));
            $this->wheres[] = [$field, '=', $arguments[0]];
            return $this;
        }
        
        // Handle orderByXxx methods
        if (str_starts_with($name, 'orderBy')) {
            $field = lcfirst(substr($name, 7));
            $field = preg_replace('/([A-Z])/', '_$1', $field);
            $field = strtolower(ltrim($field, '_'));
            $direction = $arguments[0] ?? 'ASC';
            $this->orders[] = [$field, $direction];
            return $this;
        }
        
        throw new \BadMethodCallException("Method {$name} does not exist");
    }
    
    public function table(string $table): self
    {
        $this->table = $table;
        return $this;
    }
    
    public function select(string ...$columns): self
    {
        $this->selects = $columns;
        return $this;
    }
    
    public function where(string $field, string $operator, mixed $value): self
    {
        $this->wheres[] = [$field, $operator, $value];
        return $this;
    }
    
    public function orderBy(string $field, string $direction = 'ASC'): self
    {
        $this->orders[] = [$field, $direction];
        return $this;
    }
    
    public function limit(int $limit): self
    {
        $this->limitValue = $limit;
        return $this;
    }
    
    public function toSql(): string
    {
        $sql = 'SELECT ' . implode(', ', $this->selects);
        $sql .= ' FROM ' . $this->table;
        
        if (!empty($this->wheres)) {
            $conditions = array_map(
                fn($w) => "{$w[0]} {$w[1]} ?",
                $this->wheres
            );
            $sql .= ' WHERE ' . implode(' AND ', $conditions);
        }
        
        if (!empty($this->orders)) {
            $orderClauses = array_map(
                fn($o) => "{$o[0]} {$o[1]}",
                $this->orders
            );
            $sql .= ' ORDER BY ' . implode(', ', $orderClauses);
        }
        
        if ($this->limitValue !== null) {
            $sql .= ' LIMIT ' . $this->limitValue;
        }
        
        return $sql;
    }
    
    public function getBindings(): array
    {
        return array_column($this->wheres, 2);
    }
}

// ---------------------------
// Standalone functions
// ---------------------------

function createEntity(array $data): Entity
{
    return new Entity($data);
}

function validateData(array $data, array $rules): array
{
    $validator = new Validator($rules);
    return $validator($data);
}
