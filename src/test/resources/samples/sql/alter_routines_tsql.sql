-- T-SQL ALTER FUNCTION and ALTER PROCEDURE samples
-- Tests extraction of table, view, function, and procedure references

GO

-- ALTER FUNCTION: scalar function with table and view references (unqualified)
ALTER FUNCTION fn_net_amount(@gross DECIMAL(12,2), @tax_rate_id INT)
RETURNS DECIMAL(12,2)
AS
BEGIN
    DECLARE @tax DECIMAL(12,2);
    DECLARE @discount DECIMAL(12,2);
    
    -- Table reference: tax_rates (unqualified)
    SELECT @tax = rate FROM tax_rates WHERE id = @tax_rate_id;
    
    -- View reference: active_discounts_view (unqualified)
    SELECT @discount = discount_amount 
    FROM active_discounts_view 
    WHERE applies_to = 'all';
    
    RETURN @gross - (@gross * @tax) - @discount;
END;
GO

-- ALTER PROCEDURE: with table refs, view refs, function calls, and procedure calls
ALTER PROCEDURE sales.recalc_order_total 
    @orderId INT,
    @customerId INT
AS
BEGIN
    DECLARE @subtotal DECIMAL(12,2);
    DECLARE @tax_rate_id INT;
    DECLARE @final_total DECIMAL(12,2);
    
    -- Table reference: sales.order_items
    SELECT @subtotal = SUM(quantity * unit_price)
    FROM sales.order_items
    WHERE order_id = @orderId;
    
    -- View reference: sales.customer_tax_info_view
    SELECT @tax_rate_id = tax_rate_id
    FROM sales.customer_tax_info_view
    WHERE customer_id = @customerId;
    
    -- Function call: fn_net_amount (unqualified)
    SET @final_total = fn_net_amount(@subtotal, @tax_rate_id);
    
    -- Table reference: sales.orders (UPDATE)
    UPDATE sales.orders 
    SET total = @final_total 
    WHERE id = @orderId;
    
    -- Procedure call: log_order_update (unqualified)
    EXEC log_order_update @orderId, @final_total, SYSTEM_USER;
END;
GO

-- ALTER FUNCTION: table-valued function with multiple table and view references
ALTER FUNCTION inventory.fn_get_low_stock_items(@warehouse_id INT)
RETURNS TABLE
AS
RETURN
(
    -- Table references: inventory.stock, inventory.products
    -- View reference: inventory.reorder_thresholds_view
    SELECT 
        p.product_id,
        p.product_name,
        s.quantity,
        t.threshold
    FROM inventory.stock s
    INNER JOIN inventory.products p ON s.product_id = p.product_id
    INNER JOIN inventory.reorder_thresholds_view t ON p.category_id = t.category_id
    WHERE s.warehouse_id = @warehouse_id
      AND s.quantity < t.threshold
);
GO

-- ALTER PROCEDURE: complex with nested function calls and multiple procedure calls
ALTER PROCEDURE purchasing.process_purchase_order
    @po_id INT
AS
BEGIN
    DECLARE @vendor_id INT;
    DECLARE @total_amount DECIMAL(12,2);
    
    -- Table reference: purchasing.purchase_orders
    SELECT @vendor_id = vendor_id, @total_amount = total_amount
    FROM purchasing.purchase_orders
    WHERE po_id = @po_id;
    
    -- Function call: fn_calculate_discount (nested in UPDATE, unqualified)
    -- Table reference: purchasing.purchase_orders (UPDATE)
    UPDATE purchasing.purchase_orders
    SET discounted_total = fn_calculate_discount(@total_amount, @vendor_id)
    WHERE po_id = @po_id;
    
    -- Procedure call: update_expected_stock (unqualified)
    EXEC update_expected_stock @po_id;
    
    -- Procedure call: accounting.create_payable (qualified)
    EXEC accounting.create_payable @vendor_id, @total_amount;
    
    -- View reference in EXISTS check: purchasing.approved_vendors_view
    IF EXISTS (SELECT 1 FROM purchasing.approved_vendors_view WHERE vendor_id = @vendor_id)
    BEGIN
        -- Procedure call: notifications.send_approval_notification
        EXEC notifications.send_approval_notification @po_id, 'approved';
    END
END;
GO
