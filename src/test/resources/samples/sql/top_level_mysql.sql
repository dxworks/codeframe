-- MySQL top-level statements sample

-- Top-level procedure call
CALL purchasing.process_purchase_order(123);

-- Top-level SELECT with function call and table refs
SELECT fn_net_amount(o.total_amount, 5)
FROM sales.orders o
WHERE o.status = 'PAID';
