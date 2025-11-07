-- Simple table creation
CREATE TABLE users (
    id INT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP
);

-- Table with foreign key
CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    user_id INT NOT NULL,
    total_amount DECIMAL(10, 2),
    order_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- View creation
CREATE VIEW active_users AS
SELECT id, username, email
FROM users
WHERE created_at > '2024-01-01';

-- Index creation
CREATE INDEX idx_username ON users(username);

-- Stored procedure
CREATE PROCEDURE GetUserById(IN userId INT)
BEGIN
    SELECT * FROM users WHERE id = userId;
END;

-- Function
CREATE FUNCTION CalculateTotal(orderId INT)
RETURNS DECIMAL(10, 2)
BEGIN
    DECLARE total DECIMAL(10, 2);
    SELECT total_amount INTO total FROM orders WHERE order_id = orderId;
    RETURN total;
END;
