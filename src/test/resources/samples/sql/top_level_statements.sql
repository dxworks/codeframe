
-- Top-level procedure call statement
CALL sales.recalc_order_total(123);


SELECT COALESCE(SUM(sales.get_total(o.id)), 0)::DECIMAL(12,2)
FROM sales.orders o
WHERE o.user_id = total_for_user_v2.uid
  AND EXISTS (SELECT 1 FROM sales.order_summaries s WHERE s.order_id = o.id);
