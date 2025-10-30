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
