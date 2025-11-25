-- PL/SQL top-level statements sample

-- Anonymous block with procedure call and function call in SELECT
BEGIN
    -- Top-level SELECT with schema-qualified function
    FOR rec IN (
        SELECT o.order_id,
               HR.TOTAL_AMOUNT(o.order_id) AS total_amount
        FROM HR.ORDERS o
        WHERE o.customer_id = 123
    ) LOOP
        NULL; -- no-op
    END LOOP;

    -- Direct procedure call (same schema, qualified)
    HR.GET_ORDERS_BY_CUSTOMER(123);

    -- Direct procedure call (same schema, unqualified)
    GET_ORDERS_BY_CUSTOMER(456);
END;
/

-- EXECUTE-style top-level procedure call
EXECUTE HR.REPORT_CUSTOMER_ORDERS(789);

-- Standalone top-level SELECT with additional references and function calls (currently ignored by the PL/SQL analyzer; kept as a future test case)
SELECT HR.TOTAL_AMOUNT_v2(o.order_id)   AS total_amount_qualified,
       TOTAL_AMOUNT_v2(o.order_id)      AS total_amount_unqualified
FROM HR.ORDERS_v2 o
WHERE HR.ORDER_HAS_ITEM(o.order_id, 'SKU-XYZ') = 1
  AND ORDER_HAS_ITEM(o.order_id, 'SKU-ABC') = 1;
