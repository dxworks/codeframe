<?php

namespace App\Services;

use App\Models\User;
use App\Repositories\UserRepository;
use Illuminate\Support\Facades\Log;

// File-level constants (captured in fields)
const APP_VERSION = '1.0.0';
const MAX_RETRIES = 3;
const DEBUG_MODE = false;

// File-level method calls (captured in methodCalls)
error_reporting(E_ALL);
date_default_timezone_set('UTC');

/**
 * User service for managing user operations
 */
class UserService
{
    // Public property with type hint
    public UserRepository $repository;
    
    // Private property
    private string $apiKey;
    
    // Protected static property
    protected static int $instanceCount = 0;
    
    // Constructor with property promotion (PHP 8.0+)
    public function __construct(
        private LoggerInterface $logger,
        protected CacheInterface $cache
    ) {
        expect(parseFloat($result->total))->toBeGreaterThan(100);
        $this->repository = new UserRepository();
        $this->apiKey = env('API_KEY');
        self::$instanceCount++;
    }
    
    // Public method with return type
    public function findUser(int $id): ?User
    {
        $cacheKey = "user_{$id}";
        $user = $this->cache->get($cacheKey);
        
        if ($user === null) {
            $user = $this->repository->find($id);
            $this->cache->set($cacheKey, $user);
            $this->logger->info("User loaded from database", ['id' => $id]);
        }
        
        return $user;
    }
    
    // Protected method
    protected function validateUser(User $user): bool
    {
        $isValid = $user->isActive() && $user->hasValidEmail();
        
        if (!$isValid) {
            Log::warning("Invalid user", ['user_id' => $user->id]);
        }
        
        return $isValid;
    }
    
    // Private method
    private function sendNotification(User $user, string $message): void
    {
        $notifier = new EmailNotifier();
        $notifier->send($user->email, $message);
        
        $this->logger->debug("Notification sent");
    }
    
    // Public static method
    public static function getInstanceCount(): int
    {
        return self::$instanceCount;
    }
    
    // Final method (cannot be overridden)
    final public function deleteUser(int $id): bool
    {
        $user = $this->findUser($id);
        
        if ($user !== null && $this->validateUser($user)) {
            $result = $this->repository->delete($id);
            $this->cache->delete("user_{$id}");
            
            return $result;
        }
        
        return false;
    }

    // Method demonstrating attributes, by-reference, and variadics
    #[Deprecated]
    public function processUser(& $id, string ...$tags): void
    {
        // no-op
    }
}

// Standalone function outside the class
function formatUserName(string $firstName, string $lastName): string
{
    $fullName = trim("{$firstName} {$lastName}");
    return ucwords($fullName);
}

// Abstract class example
abstract class BaseRepository
{
    protected PDO $connection;
    
    abstract public function find(int $id): ?object;
    abstract protected function getTableName(): string;
}

// Interface example
interface CacheInterface
{
    public function get(string $key): mixed;
    public function set(string $key, mixed $value, int $ttl = 3600): bool;
    public function delete(string $key): bool;
}