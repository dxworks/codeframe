<?php

namespace App\Enums;

// File-level constant
const ENUM_VERSION = '1.0.0';

/**
 * Sample demonstrating PHP 8.1+ enums for CodeFrame analysis.
 * Enums provide a way to define a set of named constants.
 */

// ---------------------------
// Unit Enum (Pure Enum)
// ---------------------------

enum Status
{
    case Pending;
    case Active;
    case Inactive;
    case Deleted;
    
    public function label(): string
    {
        return match($this) {
            self::Pending => 'Pending Approval',
            self::Active => 'Active',
            self::Inactive => 'Inactive',
            self::Deleted => 'Deleted',
        };
    }
    
    public function isActive(): bool
    {
        return $this === self::Active;
    }
    
    public static function default(): self
    {
        return self::Pending;
    }
}

// ---------------------------
// String-Backed Enum
// ---------------------------

enum Color: string
{
    case Red = 'red';
    case Green = 'green';
    case Blue = 'blue';
    case Yellow = 'yellow';
    case Black = 'black';
    case White = 'white';
    
    public function hex(): string
    {
        return match($this) {
            self::Red => '#FF0000',
            self::Green => '#00FF00',
            self::Blue => '#0000FF',
            self::Yellow => '#FFFF00',
            self::Black => '#000000',
            self::White => '#FFFFFF',
        };
    }
    
    public function rgb(): array
    {
        $hex = ltrim($this->hex(), '#');
        return [
            'r' => hexdec(substr($hex, 0, 2)),
            'g' => hexdec(substr($hex, 2, 2)),
            'b' => hexdec(substr($hex, 4, 2)),
        ];
    }
    
    public static function fromHex(string $hex): ?self
    {
        foreach (self::cases() as $case) {
            if (strtoupper($case->hex()) === strtoupper($hex)) {
                return $case;
            }
        }
        return null;
    }
}

// ---------------------------
// Integer-Backed Enum
// ---------------------------

enum HttpStatus: int
{
    case OK = 200;
    case Created = 201;
    case NoContent = 204;
    case BadRequest = 400;
    case Unauthorized = 401;
    case Forbidden = 403;
    case NotFound = 404;
    case InternalServerError = 500;
    case ServiceUnavailable = 503;
    
    public function message(): string
    {
        return match($this) {
            self::OK => 'OK',
            self::Created => 'Created',
            self::NoContent => 'No Content',
            self::BadRequest => 'Bad Request',
            self::Unauthorized => 'Unauthorized',
            self::Forbidden => 'Forbidden',
            self::NotFound => 'Not Found',
            self::InternalServerError => 'Internal Server Error',
            self::ServiceUnavailable => 'Service Unavailable',
        };
    }
    
    public function isSuccess(): bool
    {
        return $this->value >= 200 && $this->value < 300;
    }
    
    public function isClientError(): bool
    {
        return $this->value >= 400 && $this->value < 500;
    }
    
    public function isServerError(): bool
    {
        return $this->value >= 500;
    }
    
    public static function fromCode(int $code): ?self
    {
        return self::tryFrom($code);
    }
}

// ---------------------------
// Enum Implementing Interface
// ---------------------------

interface Describable
{
    public function describe(): string;
}

enum Priority: int implements Describable
{
    case Low = 1;
    case Medium = 2;
    case High = 3;
    case Critical = 4;
    
    public function describe(): string
    {
        return "Priority level: {$this->name} ({$this->value})";
    }
    
    public function getColor(): Color
    {
        return match($this) {
            self::Low => Color::Green,
            self::Medium => Color::Yellow,
            self::High => Color::Red,
            self::Critical => Color::Black,
        };
    }
    
    public static function highest(): self
    {
        return self::Critical;
    }
    
    public static function lowest(): self
    {
        return self::Low;
    }
}

// ---------------------------
// Enum with Trait
// ---------------------------

trait EnumHelpers
{
    public static function names(): array
    {
        return array_column(self::cases(), 'name');
    }
    
    public static function values(): array
    {
        return array_column(self::cases(), 'value');
    }
    
    public static function random(): self
    {
        $cases = self::cases();
        return $cases[array_rand($cases)];
    }
}

enum DayOfWeek: int
{
    use EnumHelpers;
    
    case Monday = 1;
    case Tuesday = 2;
    case Wednesday = 3;
    case Thursday = 4;
    case Friday = 5;
    case Saturday = 6;
    case Sunday = 7;
    
    public function isWeekend(): bool
    {
        return $this === self::Saturday || $this === self::Sunday;
    }
    
    public function isWeekday(): bool
    {
        return !$this->isWeekend();
    }
    
    public function next(): self
    {
        $nextValue = ($this->value % 7) + 1;
        return self::from($nextValue);
    }
    
    public function previous(): self
    {
        $prevValue = $this->value === 1 ? 7 : $this->value - 1;
        return self::from($prevValue);
    }
}

// ---------------------------
// Class Using Enums
// ---------------------------

class Task
{
    private int $id;
    private string $title;
    private Status $status;
    private Priority $priority;
    
    public function __construct(
        int $id,
        string $title,
        Status $status = Status::Pending,
        Priority $priority = Priority::Medium
    ) {
        $this->id = $id;
        $this->title = $title;
        $this->status = $status;
        $this->priority = $priority;
    }
    
    public function getId(): int
    {
        return $this->id;
    }
    
    public function getTitle(): string
    {
        return $this->title;
    }
    
    public function getStatus(): Status
    {
        return $this->status;
    }
    
    public function setStatus(Status $status): void
    {
        $this->status = $status;
    }
    
    public function getPriority(): Priority
    {
        return $this->priority;
    }
    
    public function setPriority(Priority $priority): void
    {
        $this->priority = $priority;
    }
    
    public function isHighPriority(): bool
    {
        return $this->priority === Priority::High || $this->priority === Priority::Critical;
    }
    
    public function toArray(): array
    {
        return [
            'id' => $this->id,
            'title' => $this->title,
            'status' => $this->status->name,
            'status_label' => $this->status->label(),
            'priority' => $this->priority->value,
            'priority_color' => $this->priority->getColor()->value,
        ];
    }
}

// ---------------------------
// Standalone functions
// ---------------------------

function getStatusLabel(Status $status): string
{
    return $status->label();
}

function createTask(string $title, Priority $priority = Priority::Medium): Task
{
    static $nextId = 1;
    $task = new Task($nextId++, $title, Status::Pending, $priority);
    return $task;
}
