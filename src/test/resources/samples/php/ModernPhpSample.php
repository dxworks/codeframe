<?php

namespace App\Modern;

// File-level constant
const PHP_VERSION_MIN = '8.1';

// File-level method call with named argument
ini_set(option: 'display_errors', value: '0');

/**
 * Sample demonstrating modern PHP 8.x features for CodeFrame analysis.
 * Covers readonly properties, match expressions, nullsafe operator,
 * union/intersection types, named arguments, and more.
 */

// ---------------------------
// Union Types (PHP 8.0+)
// ---------------------------

class UnionTypeExample
{
    private int|float $number;
    private string|null $name;
    private array|object $data;
    
    public function __construct(int|float $number, string|null $name = null)
    {
        $this->number = $number;
        $this->name = $name;
        $this->data = [];
    }
    
    public function getNumber(): int|float
    {
        return $this->number;
    }
    
    public function setNumber(int|float $value): void
    {
        $this->number = $value;
    }
    
    public function getName(): string|null
    {
        return $this->name;
    }
    
    public function setData(array|object $data): void
    {
        $this->data = $data;
    }
    
    public function getData(): array|object
    {
        return $this->data;
    }
    
    // Mixed type (PHP 8.0+)
    public function process(mixed $input): mixed
    {
        if (is_string($input)) {
            return strtoupper($input);
        }
        if (is_array($input)) {
            return array_values($input);
        }
        return $input;
    }
}

// ---------------------------
// Intersection Types (PHP 8.1+)
// ---------------------------

interface Countable
{
    public function count(): int;
}

interface Stringable
{
    public function __toString(): string;
}

class IntersectionTypeExample
{
    // Parameter must implement both interfaces
    public function processCollection(Countable&\Iterator $collection): array
    {
        $result = [];
        foreach ($collection as $item) {
            $result[] = $item;
        }
        return $result;
    }
    
    public function formatCountable(Countable&Stringable $item): string
    {
        return "Count: {$item->count()}, Value: {$item}";
    }
}

// ---------------------------
// Readonly Properties (PHP 8.1+)
// ---------------------------

class ImmutableUser
{
    public function __construct(
        public readonly int $id,
        public readonly string $username,
        public readonly string $email,
        public readonly \DateTimeImmutable $createdAt = new \DateTimeImmutable()
    ) {}
    
    public function withEmail(string $email): self
    {
        return new self($this->id, $this->username, $email, $this->createdAt);
    }
    
    public function toArray(): array
    {
        return [
            'id' => $this->id,
            'username' => $this->username,
            'email' => $this->email,
            'createdAt' => $this->createdAt->format('c'),
        ];
    }
}

// ---------------------------
// Readonly Class (PHP 8.2+)
// ---------------------------

readonly class ImmutableConfig
{
    public function __construct(
        public string $appName,
        public string $environment,
        public bool $debug,
        public array $settings = []
    ) {}
    
    public function get(string $key, mixed $default = null): mixed
    {
        return $this->settings[$key] ?? $default;
    }
    
    public function isProduction(): bool
    {
        return $this->environment === 'production';
    }
}

// ---------------------------
// Match Expression (PHP 8.0+)
// ---------------------------

class MatchExpressionExample
{
    public function getStatusLabel(int $status): string
    {
        return match($status) {
            0 => 'Pending',
            1 => 'Active',
            2 => 'Inactive',
            3, 4 => 'Archived',  // Multiple conditions
            default => 'Unknown',
        };
    }
    
    public function getHttpMessage(int $code): string
    {
        return match(true) {
            $code >= 200 && $code < 300 => 'Success',
            $code >= 300 && $code < 400 => 'Redirect',
            $code >= 400 && $code < 500 => 'Client Error',
            $code >= 500 => 'Server Error',
            default => 'Unknown',
        };
    }
    
    public function processValue(mixed $value): string
    {
        return match(gettype($value)) {
            'string' => "String: {$value}",
            'integer' => "Integer: {$value}",
            'double' => "Float: {$value}",
            'boolean' => "Boolean: " . ($value ? 'true' : 'false'),
            'array' => "Array with " . count($value) . " items",
            'object' => "Object of class " . get_class($value),
            'NULL' => "Null value",
            default => "Unknown type",
        };
    }
}

// ---------------------------
// Nullsafe Operator (PHP 8.0+)
// ---------------------------

class Address
{
    public function __construct(
        public ?string $street = null,
        public ?string $city = null,
        public ?string $country = null
    ) {}
    
    public function getFullAddress(): string
    {
        return implode(', ', array_filter([$this->street, $this->city, $this->country]));
    }
}

class Profile
{
    public function __construct(
        public ?Address $address = null,
        public ?string $bio = null
    ) {}
}

class NullsafeExample
{
    private ?Profile $profile = null;
    
    public function __construct(?Profile $profile = null)
    {
        $this->profile = $profile;
    }
    
    public function getCity(): ?string
    {
        // Nullsafe operator - returns null if any part is null
        return $this->profile?->address?->city;
    }
    
    public function getCountry(): ?string
    {
        return $this->profile?->address?->country;
    }
    
    public function getFullAddress(): ?string
    {
        return $this->profile?->address?->getFullAddress();
    }
    
    public function getBioLength(): ?int
    {
        return $this->profile?->bio !== null ? strlen($this->profile->bio) : null;
    }
    
    public function setProfile(?Profile $profile): void
    {
        $this->profile = $profile;
    }
}

// ---------------------------
// Named Arguments (PHP 8.0+)
// ---------------------------

class NamedArgumentsExample
{
    public function createUser(
        string $username,
        string $email,
        ?string $firstName = null,
        ?string $lastName = null,
        bool $active = true,
        array $roles = ['user'],
        ?int $age = null
    ): array {
        return [
            'username' => $username,
            'email' => $email,
            'firstName' => $firstName,
            'lastName' => $lastName,
            'active' => $active,
            'roles' => $roles,
            'age' => $age,
        ];
    }
    
    public function demonstrateNamedArgs(): array
    {
        // Using named arguments - can skip optional params and reorder
        return $this->createUser(
            email: 'john@example.com',
            username: 'johndoe',
            roles: ['admin', 'user'],
            active: true
        );
    }
    
    public function formatDate(
        int $year,
        int $month = 1,
        int $day = 1,
        int $hour = 0,
        int $minute = 0,
        int $second = 0
    ): string {
        return sprintf('%04d-%02d-%02d %02d:%02d:%02d', $year, $month, $day, $hour, $minute, $second);
    }
}

// ---------------------------
// Constructor Property Promotion (PHP 8.0+)
// ---------------------------

class PromotedPropertiesExample
{
    public function __construct(
        public int $id,
        public string $name,
        private string $secret,
        protected ?string $description = null,
        public readonly bool $immutable = false
    ) {}
    
    public function getSecret(): string
    {
        return $this->secret;
    }
    
    public function getDescription(): ?string
    {
        return $this->description;
    }
    
    public function setDescription(?string $description): void
    {
        $this->description = $description;
    }
}

// ---------------------------
// Attributes (PHP 8.0+)
// ---------------------------

#[\Attribute(\Attribute::TARGET_CLASS)]
class Entity
{
    public function __construct(
        public string $table
    ) {}
}

#[\Attribute(\Attribute::TARGET_PROPERTY)]
class Column
{
    public function __construct(
        public string $name,
        public string $type = 'string',
        public bool $nullable = false
    ) {}
}

#[\Attribute(\Attribute::TARGET_METHOD)]
class Route
{
    public function __construct(
        public string $path,
        public string $method = 'GET'
    ) {}
}

#[\Attribute(\Attribute::TARGET_PARAMETER)]
class Validated
{
    public function __construct(
        public string $rule
    ) {}
}

#[Entity(table: 'products')]
class Product
{
    #[Column(name: 'id', type: 'int')]
    public int $id;
    
    #[Column(name: 'name', type: 'varchar')]
    public string $name;
    
    #[Column(name: 'price', type: 'decimal', nullable: true)]
    public ?float $price;
    
    public function __construct(int $id, string $name, ?float $price = null)
    {
        $this->id = $id;
        $this->name = $name;
        $this->price = $price;
    }
    
    #[Route(path: '/products/{id}', method: 'GET')]
    public function show(int $id): array
    {
        return ['id' => $id, 'name' => $this->name, 'price' => $this->price];
    }
    
    #[Route(path: '/products', method: 'POST')]
    public function store(
        #[Validated(rule: 'required|string')] string $name,
        #[Validated(rule: 'numeric|min:0')] float $price
    ): array {
        $this->name = $name;
        $this->price = $price;
        return $this->show($this->id);
    }
}

// ---------------------------
// First-Class Callable Syntax (PHP 8.1+)
// ---------------------------

class FirstClassCallableExample
{
    public function add(int $a, int $b): int
    {
        return $a + $b;
    }
    
    public static function multiply(int $a, int $b): int
    {
        return $a * $b;
    }
    
    public function getCallables(): array
    {
        return [
            'add' => $this->add(...),           // First-class callable
            'multiply' => self::multiply(...),  // Static first-class callable
            'strlen' => strlen(...),            // Built-in function
        ];
    }
    
    public function applyOperation(callable $operation, int $a, int $b): int
    {
        return $operation($a, $b);
    }
    
    public function demonstrateFirstClassCallable(): array
    {
        $callables = $this->getCallables();
        
        return [
            'add_result' => $this->applyOperation($callables['add'], 5, 3),
            'multiply_result' => $this->applyOperation($callables['multiply'], 5, 3),
            'strlen_result' => $callables['strlen']('hello'),
        ];
    }
}

// ---------------------------
// New in Initializers (PHP 8.1+)
// ---------------------------

class NewInInitializersExample
{
    public function __construct(
        private \DateTimeImmutable $createdAt = new \DateTimeImmutable(),
        private array $config = ['debug' => false]
    ) {}
    
    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }
    
    public function getConfig(): array
    {
        return $this->config;
    }
}

// ---------------------------
// Standalone functions with modern features
// ---------------------------

function processInput(int|float|string $input): string
{
    return match(true) {
        is_int($input) => "Integer: {$input}",
        is_float($input) => "Float: {$input}",
        is_string($input) => "String: {$input}",
    };
}

function createImmutableUser(
    int $id,
    string $username,
    string $email
): ImmutableUser {
    return new ImmutableUser(
        id: $id,
        username: $username,
        email: $email
    );
}

function getNullsafeValue(?object $obj): ?string
{
    return $obj?->getValue();
}
