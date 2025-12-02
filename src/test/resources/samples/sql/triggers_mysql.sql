-- MySQL Trigger Examples
-- Tests BEFORE/AFTER triggers with inline body

DELIMITER $$

-- Simple BEFORE INSERT trigger
CREATE TRIGGER trg_users_before_insert
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    SET NEW.created_at = NOW();
    SET NEW.updated_at = NOW();
    INSERT INTO audit_log (table_name, action, created_at)
    VALUES ('users', 'INSERT', NOW());
END$$

-- AFTER UPDATE trigger
CREATE TRIGGER trg_orders_after_update
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO order_history (order_id, old_status, new_status, changed_at)
        VALUES (NEW.order_id, OLD.status, NEW.status, NOW());
        
        CALL notify_order_status_change(NEW.order_id, NEW.status);
    END IF;
END$$

-- BEFORE DELETE trigger
CREATE TRIGGER trg_products_before_delete
BEFORE DELETE ON products
FOR EACH ROW
BEGIN
    INSERT INTO deleted_products (product_id, product_name, deleted_at, deleted_by)
    VALUES (OLD.product_id, OLD.product_name, NOW(), CURRENT_USER());
END$$

-- AFTER INSERT trigger with function call
CREATE TRIGGER trg_order_items_after_insert
AFTER INSERT ON order_items
FOR EACH ROW
BEGIN
    UPDATE orders
    SET total_amount = calculate_order_total(NEW.order_id),
        updated_at = NOW()
    WHERE order_id = NEW.order_id;
END$$

DELIMITER ;

-- Simple trigger without delimiter change
CREATE TRIGGER trg_simple_audit
AFTER INSERT ON simple_table
FOR EACH ROW
INSERT INTO simple_audit (record_id, created_at) VALUES (NEW.id, NOW());
