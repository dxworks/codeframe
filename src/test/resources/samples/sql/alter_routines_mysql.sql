-- MySQL ALTER FUNCTION and ALTER PROCEDURE samples
-- Tests extraction of table, view, function, and procedure references

DELIMITER $$

-- ALTER FUNCTION: scalar function with table and view references (unqualified)
CREATE FUNCTION fn_net_amount(
    p_gross DECIMAL(12,2), 
    p_tax_rate_id INT
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
BEGIN
    DECLARE v_tax DECIMAL(12,2);
    DECLARE v_discount DECIMAL(12,2);
    
    -- Table reference: tax_rates (unqualified)
    SELECT rate INTO v_tax 
    FROM tax_rates 
    WHERE id = p_tax_rate_id;
    
    -- View reference: active_discounts_view (unqualified)
    SELECT discount_amount INTO v_discount
    FROM active_discounts_view 
    WHERE applies_to = 'all'
    LIMIT 1;
    
    RETURN p_gross - (p_gross * v_tax) - v_discount;
END$$

-- ALTER PROCEDURE: with table refs, view refs, function calls, and procedure calls
CREATE PROCEDURE sales.recalc_order_total(
    IN p_order_id INT,
    IN p_customer_id INT
)
BEGIN
    DECLARE v_subtotal DECIMAL(12,2);
    DECLARE v_tax_rate_id INT;
    DECLARE v_final_total DECIMAL(12,2);
    
    -- Table reference: sales.order_items
    SELECT SUM(quantity * unit_price) INTO v_subtotal
    FROM sales.order_items
    WHERE order_id = p_order_id;
    
    -- View reference: sales.customer_tax_info_view
    SELECT tax_rate_id INTO v_tax_rate_id
    FROM sales.customer_tax_info_view
    WHERE customer_id = p_customer_id;
    
    -- Function call: fn_net_amount (unqualified)
    SET v_final_total = fn_net_amount(v_subtotal, v_tax_rate_id);
    
    -- Table reference: sales.orders (UPDATE)
    UPDATE sales.orders 
    SET total = v_final_total 
    WHERE id = p_order_id;
    
    -- Procedure call: log_order_update (unqualified)
    CALL log_order_update(p_order_id, v_final_total, USER());
END$$

-- ALTER FUNCTION: function with JOIN and multiple table/view references
CREATE FUNCTION inventory.fn_get_stock_value(p_warehouse_id INT)
RETURNS DECIMAL(12,2)
READS SQL DATA
BEGIN
    DECLARE v_total DECIMAL(12,2);
    
    -- Table references: inventory.stock, inventory.products
    -- View reference: inventory.current_prices_view
    SELECT SUM(s.quantity * p.unit_cost) INTO v_total
    FROM inventory.stock s
    INNER JOIN inventory.products pr ON s.product_id = pr.product_id
    INNER JOIN inventory.current_prices_view p ON pr.product_id = p.product_id
    WHERE s.warehouse_id = p_warehouse_id;
    
    RETURN IFNULL(v_total, 0);
END$$

-- ALTER PROCEDURE: complex with nested function calls and multiple procedure calls
CREATE PROCEDURE purchasing.process_purchase_order(IN p_po_id INT)
BEGIN
    DECLARE v_vendor_id INT;
    DECLARE v_total_amount DECIMAL(12,2);
    DECLARE v_discounted DECIMAL(12,2);
    DECLARE v_is_approved INT;
    
    -- Table reference: purchasing.purchase_orders
    SELECT vendor_id, total_amount INTO v_vendor_id, v_total_amount
    FROM purchasing.purchase_orders
    WHERE po_id = p_po_id;
    
    -- Function call: fn_calculate_discount (unqualified)
    SET v_discounted = fn_calculate_discount(v_total_amount, v_vendor_id);
    
    -- Table reference: purchasing.purchase_orders (UPDATE)
    UPDATE purchasing.purchase_orders
    SET discounted_total = v_discounted
    WHERE po_id = p_po_id;
    
    -- Procedure call: update_expected_stock (unqualified)
    CALL update_expected_stock(p_po_id);
    
    -- Procedure call: accounting.create_payable (qualified)
    CALL accounting.create_payable(v_vendor_id, v_total_amount);
    
    -- View reference in EXISTS check: purchasing.approved_vendors_view
    SELECT COUNT(*) INTO v_is_approved
    FROM purchasing.approved_vendors_view 
    WHERE vendor_id = v_vendor_id;
    
    IF v_is_approved > 0 THEN
        -- Procedure call: notifications.send_approval_notification
        CALL notifications.send_approval_notification(p_po_id, 'approved');
    END IF;
END$$

DELIMITER ;

-- ALTER PROCEDURE: metadata change only (characteristics)
ALTER PROCEDURE warehouse.refresh_stock COMMENT 'Updated behavior - recalculates stock levels';

-- ALTER FUNCTION: metadata change only (characteristics)
ALTER FUNCTION fn_net_amount COMMENT 'Calculates net amount after tax and discount';
