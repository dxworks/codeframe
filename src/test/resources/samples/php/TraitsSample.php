<?php

namespace App\Traits;

// File-level constant
const TRAIT_VERSION = '1.0.0';

// File-level method call
error_reporting(E_ALL);

/**
 * Sample demonstrating PHP traits for CodeFrame analysis.
 * Traits are PHP's mechanism for code reuse in single inheritance.
 */

// ---------------------------
// Basic Trait
// ---------------------------

trait Loggable
{
    protected string $logPrefix = '[LOG]';
    
    public function log(string $message): void
    {
        $timestamp = date('Y-m-d H:i:s');
        echo "{$this->logPrefix} [{$timestamp}] {$message}\n";
    }
    
    public function logError(string $message): void
    {
        $this->log("ERROR: {$message}");
    }
    
    protected function formatLogMessage(string $message): string
    {
        return trim($message);
    }
}

// ---------------------------
// Trait with Abstract Method
// ---------------------------

trait Identifiable
{
    abstract public function getId(): int;
    
    public function getIdentifier(): string
    {
        return "ID-" . $this->getId();
    }
    
    public function isSameAs(object $other): bool
    {
        if (!method_exists($other, 'getId')) {
            return false;
        }
        return $this->getId() === $other->getId();
    }
}

// ---------------------------
// Trait with Static Members
// ---------------------------

trait Countable
{
    private static int $instanceCount = 0;
    
    public static function getInstanceCount(): int
    {
        return self::$instanceCount;
    }
    
    protected static function incrementCount(): void
    {
        self::$instanceCount++;
    }
    
    protected static function decrementCount(): void
    {
        self::$instanceCount--;
    }
}

// ---------------------------
// Trait with Conflicting Method Names
// ---------------------------

trait Serializable
{
    public function serialize(): string
    {
        return json_encode($this->toArray());
    }
    
    public function toArray(): array
    {
        return get_object_vars($this);
    }
}

trait JsonExportable
{
    public function serialize(): string
    {
        return json_encode($this->getData(), JSON_PRETTY_PRINT);
    }
    
    public function getData(): array
    {
        return ['data' => get_object_vars($this)];
    }
}

// ---------------------------
// Trait Composition (Trait using other Traits)
// ---------------------------

trait FullyFeatured
{
    use Loggable;
    use Identifiable;
    
    public function describe(): string
    {
        $id = $this->getIdentifier();
        $this->log("Describing entity: {$id}");
        return "Entity with {$id}";
    }
}

// ---------------------------
// Class Using Single Trait
// ---------------------------

class SimpleLogger
{
    use Loggable;
    
    private string $name;
    
    public function __construct(string $name)
    {
        $this->name = $name;
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function logWithName(string $message): void
    {
        $this->log("[{$this->name}] {$message}");
    }
}

// ---------------------------
// Class Using Multiple Traits
// ---------------------------

class User
{
    use Loggable;
    use Identifiable;
    use Countable;
    
    private int $id;
    private string $username;
    private string $email;
    
    public function __construct(int $id, string $username, string $email)
    {
        $this->id = $id;
        $this->username = $username;
        $this->email = $email;
        self::incrementCount();
        $this->log("User created: {$username}");
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function getUsername(): string
    {
        return $this->username;
    }
    
    public function getEmail(): string
    {
        return $this->email;
    }
    
    public function __destruct()
    {
        self::decrementCount();
    }
}

// ---------------------------
// Class with Trait Conflict Resolution
// ---------------------------

class DataExporter
{
    use Serializable, JsonExportable {
        Serializable::serialize insteadof JsonExportable;
        JsonExportable::serialize as jsonSerialize;
        Serializable::toArray as public;
    }
    
    private string $name;
    private array $items;
    
    public function __construct(string $name, array $items = [])
    {
        $this->name = $name;
        $this->items = $items;
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function addItem(mixed $item): void
    {
        $this->items[] = $item;
    }
    
    public function exportAsJson(): string
    {
        return $this->jsonSerialize();
    }
    
    public function exportAsArray(): array
    {
        return $this->toArray();
    }
}

// ---------------------------
// Class with Visibility Override
// ---------------------------

class SecureEntity
{
    use Loggable {
        log as private privateLog;
        logError as private;
    }
    
    private int $id;
    
    public function __construct(int $id)
    {
        $this->id = $id;
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function logSecure(string $message): void
    {
        // Using the renamed private method
        $this->privateLog("[SECURE] {$message}");
    }
}

// ---------------------------
// Class Using Composed Trait
// ---------------------------

class Product
{
    use FullyFeatured;
    use Countable;
    
    private int $id;
    private string $name;
    private float $price;
    
    public function __construct(int $id, string $name, float $price)
    {
        $this->id = $id;
        $this->name = $name;
        $this->price = $price;
        self::incrementCount();
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function getName(): string
    {
        return $this->name;
    }
    
    public function getPrice(): float
    {
        return $this->price;
    }
    
    public function getFormattedPrice(): string
    {
        return number_format($this->price, 2) . ' USD';
    }
}

// ---------------------------
// Standalone function
// ---------------------------

function createUser(int $id, string $username, string $email): User
{
    $user = new User($id, $username, $email);
    return $user;
}
