<?php

namespace App\Exceptions;

// File-level constant
const MAX_RETRY_ATTEMPTS = 3;

// File-level method call
set_exception_handler(function(\Throwable $e) {
    error_log("Uncaught exception: " . $e->getMessage());
});

/**
 * Sample demonstrating PHP exception handling for CodeFrame analysis.
 * Covers try/catch/finally, custom exceptions, exception chaining,
 * and multiple catch blocks.
 */

// ---------------------------
// Custom Exception Classes
// ---------------------------

class ApplicationException extends \Exception
{
    protected string $errorCode;
    protected array $context = [];
    
    public function __construct(
        string $message,
        string $errorCode = 'APP_ERROR',
        array $context = [],
        int $code = 0,
        ?\Throwable $previous = null
    ) {
        parent::__construct($message, $code, $previous);
        $this->errorCode = $errorCode;
        $this->context = $context;
    }
    
    public function getErrorCode(): string
    {
        return $this->errorCode;
    }
    
    public function getContext(): array
    {
        return $this->context;
    }
    
    public function toArray(): array
    {
        return [
            'errorCode' => $this->errorCode,
            'message' => $this->getMessage(),
            'context' => $this->context,
            'file' => $this->getFile(),
            'line' => $this->getLine(),
        ];
    }
}

class ValidationException extends ApplicationException
{
    private array $errors;
    
    public function __construct(array $errors, string $message = 'Validation failed')
    {
        parent::__construct($message, 'VALIDATION_ERROR', ['errors' => $errors]);
        $this->errors = $errors;
    }
    
    public function getErrors(): array
    {
        return $this->errors;
    }
    
    public function hasError(string $field): bool
    {
        return isset($this->errors[$field]);
    }
    
    public function getFieldError(string $field): ?string
    {
        return $this->errors[$field] ?? null;
    }
}

class NotFoundException extends ApplicationException
{
    private string $resourceType;
    private mixed $resourceId;
    
    public function __construct(string $resourceType, mixed $resourceId)
    {
        $message = "{$resourceType} with ID '{$resourceId}' not found";
        parent::__construct($message, 'NOT_FOUND', [
            'resourceType' => $resourceType,
            'resourceId' => $resourceId,
        ], 404);
        $this->resourceType = $resourceType;
        $this->resourceId = $resourceId;
    }
    
    public function getResourceType(): string
    {
        return $this->resourceType;
    }
    
    public function getResourceId(): mixed
    {
        return $this->resourceId;
    }
}

class DatabaseException extends ApplicationException
{
    private ?string $query;
    
    public function __construct(string $message, ?string $query = null, ?\Throwable $previous = null)
    {
        parent::__construct($message, 'DATABASE_ERROR', ['query' => $query], 500, $previous);
        $this->query = $query;
    }
    
    public function getQuery(): ?string
    {
        return $this->query;
    }
}

class AuthenticationException extends ApplicationException
{
    public function __construct(string $message = 'Authentication required')
    {
        parent::__construct($message, 'AUTH_ERROR', [], 401);
    }
}

class AuthorizationException extends ApplicationException
{
    private string $requiredPermission;
    
    public function __construct(string $requiredPermission)
    {
        $message = "Permission denied: {$requiredPermission} required";
        parent::__construct($message, 'FORBIDDEN', ['permission' => $requiredPermission], 403);
        $this->requiredPermission = $requiredPermission;
    }
    
    public function getRequiredPermission(): string
    {
        return $this->requiredPermission;
    }
}

// ---------------------------
// Service Classes with Exception Handling
// ---------------------------

class UserService
{
    private array $users = [];
    
    public function __construct()
    {
        // Initialize with some test data
        $this->users = [
            1 => ['id' => 1, 'name' => 'John', 'email' => 'john@example.com'],
            2 => ['id' => 2, 'name' => 'Jane', 'email' => 'jane@example.com'],
        ];
    }
    
    public function findById(int $id): array
    {
        if (!isset($this->users[$id])) {
            throw new NotFoundException('User', $id);
        }
        return $this->users[$id];
    }
    
    public function create(array $data): array
    {
        $errors = $this->validate($data);
        if (!empty($errors)) {
            throw new ValidationException($errors);
        }
        
        $id = max(array_keys($this->users)) + 1;
        $user = [
            'id' => $id,
            'name' => $data['name'],
            'email' => $data['email'],
        ];
        $this->users[$id] = $user;
        return $user;
    }
    
    private function validate(array $data): array
    {
        $errors = [];
        
        if (empty($data['name'])) {
            $errors['name'] = 'Name is required';
        }
        
        if (empty($data['email'])) {
            $errors['email'] = 'Email is required';
        } elseif (!filter_var($data['email'], FILTER_VALIDATE_EMAIL)) {
            $errors['email'] = 'Invalid email format';
        }
        
        return $errors;
    }
    
    public function delete(int $id): void
    {
        if (!isset($this->users[$id])) {
            throw new NotFoundException('User', $id);
        }
        unset($this->users[$id]);
    }
}

class DatabaseConnection
{
    private bool $connected = false;
    private bool $inTransaction = false;
    
    public function connect(): void
    {
        // Simulate connection
        $this->connected = true;
    }
    
    public function disconnect(): void
    {
        $this->connected = false;
    }
    
    public function isConnected(): bool
    {
        return $this->connected;
    }
    
    public function beginTransaction(): void
    {
        if (!$this->connected) {
            throw new DatabaseException('Not connected to database');
        }
        $this->inTransaction = true;
    }
    
    public function commit(): void
    {
        if (!$this->inTransaction) {
            throw new DatabaseException('No active transaction');
        }
        $this->inTransaction = false;
    }
    
    public function rollback(): void
    {
        if (!$this->inTransaction) {
            throw new DatabaseException('No active transaction');
        }
        $this->inTransaction = false;
    }
    
    public function query(string $sql): array
    {
        if (!$this->connected) {
            throw new DatabaseException('Not connected to database', $sql);
        }
        
        // Simulate query execution
        if (str_contains($sql, 'INVALID')) {
            throw new DatabaseException('Invalid SQL syntax', $sql);
        }
        
        return ['rows' => []];
    }
}

// ---------------------------
// Exception Handling Patterns
// ---------------------------

class ExceptionHandler
{
    private array $handlers = [];
    
    public function register(string $exceptionClass, callable $handler): void
    {
        $this->handlers[$exceptionClass] = $handler;
    }
    
    public function handle(\Throwable $e): mixed
    {
        $class = get_class($e);
        
        // Try exact match first
        if (isset($this->handlers[$class])) {
            return $this->handlers[$class]($e);
        }
        
        // Try parent classes
        foreach ($this->handlers as $handlerClass => $handler) {
            if ($e instanceof $handlerClass) {
                return $handler($e);
            }
        }
        
        // Default handling
        return $this->defaultHandler($e);
    }
    
    private function defaultHandler(\Throwable $e): array
    {
        return [
            'error' => true,
            'message' => $e->getMessage(),
            'code' => $e->getCode(),
        ];
    }
}

class RetryableOperation
{
    private int $maxAttempts;
    private int $delayMs;
    
    public function __construct(int $maxAttempts = 3, int $delayMs = 100)
    {
        $this->maxAttempts = $maxAttempts;
        $this->delayMs = $delayMs;
    }
    
    public function execute(callable $operation): mixed
    {
        $lastException = null;
        
        for ($attempt = 1; $attempt <= $this->maxAttempts; $attempt++) {
            try {
                return $operation();
            } catch (\RuntimeException $e) {
                $lastException = $e;
                if ($attempt < $this->maxAttempts) {
                    usleep($this->delayMs * 1000);
                }
            }
        }
        
        throw new ApplicationException(
            "Operation failed after {$this->maxAttempts} attempts",
            'RETRY_EXHAUSTED',
            ['attempts' => $this->maxAttempts],
            0,
            $lastException
        );
    }
}

// ---------------------------
// Class Demonstrating Various Exception Patterns
// ---------------------------

class OrderProcessor
{
    private UserService $userService;
    private DatabaseConnection $db;
    
    public function __construct(UserService $userService, DatabaseConnection $db)
    {
        $this->userService = $userService;
        $this->db = $db;
    }
    
    public function processOrder(int $userId, array $items): array
    {
        // Basic try-catch
        try {
            $user = $this->userService->findById($userId);
        } catch (NotFoundException $e) {
            throw new ValidationException(['userId' => 'User not found']);
        }
        
        // Try-catch-finally with transaction
        $this->db->connect();
        $this->db->beginTransaction();
        
        try {
            $order = $this->createOrder($user, $items);
            $this->db->commit();
            return $order;
        } catch (DatabaseException $e) {
            $this->db->rollback();
            throw $e;
        } finally {
            $this->db->disconnect();
        }
    }
    
    private function createOrder(array $user, array $items): array
    {
        // Simulate order creation
        return [
            'id' => rand(1000, 9999),
            'userId' => $user['id'],
            'items' => $items,
            'status' => 'created',
        ];
    }
    
    public function processWithMultipleCatch(int $userId): array
    {
        try {
            $user = $this->userService->findById($userId);
            $result = $this->db->query("SELECT * FROM orders WHERE user_id = {$userId}");
            return ['user' => $user, 'orders' => $result];
        } catch (NotFoundException $e) {
            // Handle not found
            return ['error' => 'User not found', 'code' => 404];
        } catch (DatabaseException $e) {
            // Handle database errors
            return ['error' => 'Database error', 'code' => 500, 'query' => $e->getQuery()];
        } catch (ApplicationException $e) {
            // Handle other application errors
            return ['error' => $e->getMessage(), 'code' => $e->getCode()];
        } catch (\Throwable $e) {
            // Catch-all for any other errors
            return ['error' => 'Unexpected error', 'code' => 500];
        }
    }
    
    public function processWithRethrow(int $userId): array
    {
        try {
            return $this->processWithMultipleCatch($userId);
        } catch (ApplicationException $e) {
            // Log and rethrow
            error_log("Error processing user {$userId}: " . $e->getMessage());
            throw $e;
        }
    }
    
    public function processWithExceptionChaining(int $userId): array
    {
        try {
            $this->db->connect();
            return $this->db->query("SELECT * FROM users WHERE id = {$userId}");
        } catch (DatabaseException $e) {
            // Wrap in a more specific exception
            throw new ApplicationException(
                "Failed to fetch user data",
                'USER_FETCH_ERROR',
                ['userId' => $userId],
                0,
                $e  // Chain the original exception
            );
        }
    }
}

// ---------------------------
// Standalone functions
// ---------------------------

function safeExecute(callable $fn): mixed
{
    try {
        return $fn();
    } catch (\Throwable $e) {
        error_log("Error: " . $e->getMessage());
        return null;
    }
}

function validateOrThrow(array $data, array $rules): void
{
    $errors = [];
    foreach ($rules as $field => $rule) {
        if ($rule === 'required' && empty($data[$field])) {
            $errors[$field] = "{$field} is required";
        }
    }
    
    if (!empty($errors)) {
        throw new ValidationException($errors);
    }
}
