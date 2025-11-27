<?php

namespace App\Inheritance;

// File-level constant
const INHERITANCE_VERSION = '1.0.0';

/**
 * Sample demonstrating PHP inheritance patterns for CodeFrame analysis.
 * Covers abstract classes, interfaces, multiple interface implementation,
 * trait usage, and method overriding.
 */

// ---------------------------
// Interfaces
// ---------------------------

interface Identifiable
{
    public function getId(): int;
    public function getUuid(): string;
}

interface Timestampable
{
    public function getCreatedAt(): \DateTimeInterface;
    public function getUpdatedAt(): ?\DateTimeInterface;
    public function touch(): void;
}

interface Serializable
{
    public function serialize(): string;
    public static function deserialize(string $data): self;
}

interface Comparable
{
    public function compareTo(object $other): int;
    public function equals(object $other): bool;
}

// Interface extending multiple interfaces
interface Entity extends Identifiable, Timestampable, Serializable
{
    public function toArray(): array;
}

// ---------------------------
// Traits for Reusable Behavior
// ---------------------------

trait HasTimestamps
{
    protected \DateTimeInterface $createdAt;
    protected ?\DateTimeInterface $updatedAt = null;
    
    public function getCreatedAt(): \DateTimeInterface
    {
        return $this->createdAt;
    }
    
    public function getUpdatedAt(): ?\DateTimeInterface
    {
        return $this->updatedAt;
    }
    
    public function touch(): void
    {
        $this->updatedAt = new \DateTimeImmutable();
    }
    
    protected function initializeTimestamps(): void
    {
        $this->createdAt = new \DateTimeImmutable();
    }
}

trait HasUuid
{
    protected string $uuid;
    
    public function getUuid(): string
    {
        return $this->uuid;
    }
    
    protected function generateUuid(): void
    {
        $this->uuid = sprintf(
            '%04x%04x-%04x-%04x-%04x-%04x%04x%04x',
            mt_rand(0, 0xffff), mt_rand(0, 0xffff),
            mt_rand(0, 0xffff),
            mt_rand(0, 0x0fff) | 0x4000,
            mt_rand(0, 0x3fff) | 0x8000,
            mt_rand(0, 0xffff), mt_rand(0, 0xffff), mt_rand(0, 0xffff)
        );
    }
}

// ---------------------------
// Abstract Base Class
// ---------------------------

abstract class BaseEntity implements Entity, Comparable
{
    use HasTimestamps;
    use HasUuid;
    
    protected int $id;
    
    public function __construct(int $id)
    {
        $this->id = $id;
        $this->generateUuid();
        $this->initializeTimestamps();
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function serialize(): string
    {
        return json_encode($this->toArray());
    }
    
    public function compareTo(object $other): int
    {
        if (!$other instanceof self) {
            throw new \InvalidArgumentException('Cannot compare different types');
        }
        return $this->id <=> $other->id;
    }
    
    public function equals(object $other): bool
    {
        if (!$other instanceof static) {
            return false;
        }
        return $this->uuid === $other->uuid;
    }
    
    // Abstract method to be implemented by subclasses
    abstract public function getType(): string;
    
    // Template method pattern
    public function toArray(): array
    {
        return array_merge(
            $this->getBaseData(),
            $this->getSpecificData()
        );
    }
    
    protected function getBaseData(): array
    {
        return [
            'id' => $this->id,
            'uuid' => $this->uuid,
            'type' => $this->getType(),
            'createdAt' => $this->createdAt->format('c'),
            'updatedAt' => $this->updatedAt?->format('c'),
        ];
    }
    
    // Hook method for subclasses
    protected function getSpecificData(): array
    {
        return [];
    }
}

// ---------------------------
// Concrete Classes
// ---------------------------

class User extends BaseEntity
{
    private string $username;
    private string $email;
    private bool $active = true;
    
    public function __construct(int $id, string $username, string $email)
    {
        parent::__construct($id);
        $this->username = $username;
        $this->email = $email;
    }
    
    public function getType(): string
    {
        return 'user';
    }
    
    public function getUsername(): string
    {
        return $this->username;
    }
    
    public function getEmail(): string
    {
        return $this->email;
    }
    
    public function isActive(): bool
    {
        return $this->active;
    }
    
    public function activate(): void
    {
        $this->active = true;
        $this->touch();
    }
    
    public function deactivate(): void
    {
        $this->active = false;
        $this->touch();
    }
    
    protected function getSpecificData(): array
    {
        return [
            'username' => $this->username,
            'email' => $this->email,
            'active' => $this->active,
        ];
    }
    
    public static function deserialize(string $data): self
    {
        $arr = json_decode($data, true);
        return new self($arr['id'], $arr['username'], $arr['email']);
    }
}

class Product extends BaseEntity
{
    private string $name;
    private float $price;
    private int $stock;
    
    public function __construct(int $id, string $name, float $price, int $stock = 0)
    {
        parent::__construct($id);
        $this->name = $name;
        $this->price = $price;
        $this->stock = $stock;
    }
    
    public function getType(): string
    {
        return 'product';
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function getPrice(): float
    {
        return $this->price;
    }
    
    public function getStock(): int
    {
        return $this->stock;
    }
    
    public function setPrice(float $price): void
    {
        $this->price = $price;
        $this->touch();
    }
    
    public function addStock(int $quantity): void
    {
        $this->stock += $quantity;
        $this->touch();
    }
    
    public function removeStock(int $quantity): bool
    {
        if ($this->stock < $quantity) {
            return false;
        }
        $this->stock -= $quantity;
        $this->touch();
        return true;
    }
    
    public function isInStock(): bool
    {
        return $this->stock > 0;
    }
    
    protected function getSpecificData(): array
    {
        return [
            'name' => $this->name,
            'price' => $this->price,
            'stock' => $this->stock,
            'inStock' => $this->isInStock(),
        ];
    }
    
    public static function deserialize(string $data): self
    {
        $arr = json_decode($data, true);
        return new self($arr['id'], $arr['name'], $arr['price'], $arr['stock'] ?? 0);
    }
}

// ---------------------------
// Multi-Level Inheritance
// ---------------------------

class AdminUser extends User
{
    private array $permissions = [];
    private string $role;
    
    public function __construct(int $id, string $username, string $email, string $role = 'admin')
    {
        parent::__construct($id, $username, $email);
        $this->role = $role;
    }
    
    public function getType(): string
    {
        return 'admin_user';
    }
    
    public function getRole(): string
    {
        return $this->role;
    }
    
    public function getPermissions(): array
    {
        return $this->permissions;
    }
    
    public function addPermission(string $permission): void
    {
        if (!in_array($permission, $this->permissions, true)) {
            $this->permissions[] = $permission;
            $this->touch();
        }
    }
    
    public function removePermission(string $permission): void
    {
        $this->permissions = array_filter(
            $this->permissions,
            fn($p) => $p !== $permission
        );
        $this->touch();
    }
    
    public function hasPermission(string $permission): bool
    {
        return in_array($permission, $this->permissions, true);
    }
    
    protected function getSpecificData(): array
    {
        return array_merge(parent::getSpecificData(), [
            'role' => $this->role,
            'permissions' => $this->permissions,
        ]);
    }
    
    public static function deserialize(string $data): self
    {
        $arr = json_decode($data, true);
        return new self($arr['id'], $arr['username'], $arr['email'], $arr['role'] ?? 'admin');
    }
}

// ---------------------------
// Final Class (Cannot be Extended)
// ---------------------------

final class ImmutableConfig implements Serializable
{
    private array $settings;
    
    public function __construct(array $settings)
    {
        $this->settings = $settings;
    }
    
    public function get(string $key, mixed $default = null): mixed
    {
        return $this->settings[$key] ?? $default;
    }
    
    public function has(string $key): bool
    {
        return isset($this->settings[$key]);
    }
    
    public function all(): array
    {
        return $this->settings;
    }
    
    public function serialize(): string
    {
        return json_encode($this->settings);
    }
    
    public static function deserialize(string $data): self
    {
        return new self(json_decode($data, true));
    }
}

// ---------------------------
// Anonymous Class Example
// ---------------------------

class ServiceContainer
{
    private array $services = [];
    
    public function register(string $name, callable $factory): void
    {
        $this->services[$name] = $factory;
    }
    
    public function get(string $name): object
    {
        if (!isset($this->services[$name])) {
            throw new \RuntimeException("Service not found: {$name}");
        }
        return $this->services[$name]($this);
    }
    
    public function createLogger(): object
    {
        // Anonymous class implementing an interface
        return new class implements Serializable {
            private array $logs = [];
            
            public function log(string $message): void
            {
                $this->logs[] = [
                    'time' => date('c'),
                    'message' => $message,
                ];
            }
            
            public function getLogs(): array
            {
                return $this->logs;
            }
            
            public function serialize(): string
            {
                return json_encode($this->logs);
            }
            
            public static function deserialize(string $data): self
            {
                $instance = new self();
                $instance->logs = json_decode($data, true);
                return $instance;
            }
        };
    }
}

// ---------------------------
// Standalone functions
// ---------------------------

function createUser(int $id, string $username, string $email): User
{
    return new User($id, $username, $email);
}

function compareEntities(BaseEntity $a, BaseEntity $b): int
{
    return $a->compareTo($b);
}
