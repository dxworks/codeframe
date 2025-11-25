-- PostgreSQL ALTER FUNCTION and ALTER PROCEDURE samples
-- Tests extraction of table, view, function, and procedure references

-- ALTER FUNCTION: scalar function with table and view references (unqualified)
CREATE OR REPLACE FUNCTION fn_net_amount(
    p_gross DECIMAL(12,2), 
    p_tax_rate_id INT
)
RETURNS DECIMAL(12,2)
LANGUAGE plpgsql
AS $$
DECLARE
    v_tax DECIMAL(12,2);
    v_discount DECIMAL(12,2);
BEGIN
    -- Table reference: tax_rates (unqualified)
    SELECT rate INTO v_tax 
    FROM tax_rates 
    WHERE id = p_tax_rate_id;
    
    -- View reference: active_discounts_view (unqualified)
    SELECT discount_amount INTO v_discount
    FROM active_discounts_view 
    WHERE applies_to = 'all';
    
    RETURN p_gross - (p_gross * v_tax) - v_discount;
END;
$$;

-- ALTER PROCEDURE: with table refs, view refs, function calls, and procedure calls
CREATE OR REPLACE PROCEDURE sales.recalc_order_total(
    p_order_id INT,
    p_customer_id INT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_subtotal DECIMAL(12,2);
    v_tax_rate_id INT;
    v_final_total DECIMAL(12,2);
BEGIN
    -- Table reference: sales.order_items
    SELECT SUM(quantity * unit_price) INTO v_subtotal
    FROM sales.order_items
    WHERE order_id = p_order_id;
    
    -- View reference: sales.customer_tax_info_view
    SELECT tax_rate_id INTO v_tax_rate_id
    FROM sales.customer_tax_info_view
    WHERE customer_id = p_customer_id;
    
    -- Function call: fn_net_amount (unqualified)
    v_final_total := fn_net_amount(v_subtotal, v_tax_rate_id);
    
    -- Table reference: sales.orders (UPDATE)
    UPDATE sales.orders 
    SET total = v_final_total 
    WHERE id = p_order_id;
    
    -- Procedure call: log_order_update (unqualified)
    CALL log_order_update(p_order_id, v_final_total, current_user);
END;
$$;

-- ALTER FUNCTION: set-returning function with multiple table and view references
CREATE OR REPLACE FUNCTION inventory.fn_get_low_stock_items(p_warehouse_id INT)
RETURNS TABLE (
    product_id INT,
    product_name VARCHAR,
    quantity INT,
    threshold INT
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
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
    WHERE s.warehouse_id = p_warehouse_id
      AND s.quantity < t.threshold;
END;
$$;

-- ALTER PROCEDURE: complex with nested function calls and multiple procedure calls
CREATE OR REPLACE PROCEDURE purchasing.process_purchase_order(p_po_id INT)
LANGUAGE plpgsql
AS $$
DECLARE
    v_vendor_id INT;
    v_total_amount DECIMAL(12,2);
    v_discounted DECIMAL(12,2);
BEGIN
    -- Table reference: purchasing.purchase_orders
    SELECT vendor_id, total_amount INTO v_vendor_id, v_total_amount
    FROM purchasing.purchase_orders
    WHERE po_id = p_po_id;
    
    -- Function call: fn_calculate_discount (unqualified)
    v_discounted := fn_calculate_discount(v_total_amount, v_vendor_id);
    
    -- Table reference: purchasing.purchase_orders (UPDATE)
    UPDATE purchasing.purchase_orders
    SET discounted_total = v_discounted
    WHERE po_id = p_po_id;
    
    -- Procedure call: update_expected_stock (unqualified)
    CALL update_expected_stock(p_po_id);
    
    -- Procedure call: accounting.create_payable (qualified)
    CALL accounting.create_payable(v_vendor_id, v_total_amount);
    
    -- View reference in EXISTS check: purchasing.approved_vendors_view
    IF EXISTS (SELECT 1 FROM purchasing.approved_vendors_view WHERE vendor_id = v_vendor_id) THEN
        -- Procedure call: notifications.send_approval_notification
        CALL notifications.send_approval_notification(p_po_id, 'approved');
    END IF;
END;
$$;

-- ALTER FUNCTION: metadata change only (rename)
ALTER FUNCTION sales.get_total(integer) RENAME TO get_total_v2;

-- ALTER FUNCTION: change owner
ALTER FUNCTION inventory.fn_get_low_stock_items(integer) OWNER TO inventory_admin;
