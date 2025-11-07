-- Functions sample across dialects

-- PostgreSQL function (schema-qualified), dollar-quoted body
CREATE FUNCTION sales.get_total(order_id INT) RETURNS DECIMAL(12,2)
LANGUAGE SQL
AS $$
SELECT COALESCE(SUM(amount), 0)::DECIMAL(12,2)
FROM sales.order_lines
WHERE order_id = get_total.order_id;
$$;

-- PostgreSQL function (no schema), references other tables in body
CREATE FUNCTION total_for_user(uid INT) RETURNS DECIMAL(12,2)
LANGUAGE SQL
AS $$
SELECT COALESCE(SUM(ol.amount), 0)::DECIMAL(12,2)
FROM sales.orders o
JOIN sales.order_lines ol ON ol.order_id = o.id
JOIN public.users u ON u.id = o.user_id
WHERE u.id = total_for_user.uid;
$$;

-- View used by functions and procedures
CREATE VIEW sales.order_summaries AS
SELECT o.id AS order_id, COALESCE(SUM(ol.amount), 0)::DECIMAL(12,2) AS total
FROM sales.orders o
LEFT JOIN sales.order_lines ol ON ol.order_id = o.id
GROUP BY o.id;

-- SQL function that calls another function and references a view
CREATE FUNCTION total_for_user_v2(uid INT) RETURNS DECIMAL(12,2)
LANGUAGE SQL
AS $$
SELECT COALESCE(SUM(sales.get_total(o.id)), 0)::DECIMAL(12,2)
FROM sales.orders o
WHERE o.user_id = total_for_user_v2.uid
  AND EXISTS (SELECT 1 FROM sales.order_summaries s WHERE s.order_id = o.id);
$$;

-- PostgreSQL stored procedure (schema-qualified), references tables in body
CREATE PROCEDURE sales.recalc_order_total(IN p_order_id INT)
LANGUAGE plpgsql
AS $$
BEGIN
    -- Update order total based on order lines
    UPDATE sales.orders o
    SET total = sales.get_total(p_order_id)
    WHERE o.id = p_order_id;

    -- Call another procedure
    CALL sales.notify_order_update(p_order_id);
END;
$$;

-- Helper procedure invoked from recalc_order_total
CREATE PROCEDURE sales.notify_order_update(IN p_order_id INT)
LANGUAGE plpgsql
AS $$
BEGIN
    -- no-op for sample
    PERFORM 1;
END;
$$;