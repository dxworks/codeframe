-- PL/SQL routine bodies sample for analyzer tests

-- Procedure that selects from two tables, calls a scalar function, and logs access
CREATE OR REPLACE PROCEDURE HR.GET_ORDERS_BY_CUSTOMER (
    p_customer_id IN NUMBER
) AS
BEGIN
    -- Table refs: HR.ORDERS, HR.ORDER_ITEMS
    -- Function call: HR.TOTAL_AMOUNT
    SELECT o.order_id,
           o.order_date,
           HR.TOTAL_AMOUNT(o.order_id) AS total_amount
    FROM HR.ORDERS o
    JOIN HR.ORDER_ITEMS i ON i.order_id = o.order_id
    WHERE o.customer_id = p_customer_id;

    -- Procedure call: HR.LOG_ACCESS
    HR.LOG_ACCESS(p_who => USER, p_what => 'GET_ORDERS_BY_CUSTOMER');
END;
/

-- Scalar function used by the procedure above
CREATE OR REPLACE FUNCTION HR.TOTAL_AMOUNT (
    p_order_id IN NUMBER
) RETURN NUMBER AS
    v_total NUMBER;
BEGIN
    SELECT SUM(i.quantity * i.price)
      INTO v_total
      FROM HR.ORDER_ITEMS i
     WHERE i.order_id = p_order_id;

    RETURN v_total;
END;
/

-- Boolean-like function demonstrating EXISTS (returns 1/0)
CREATE OR REPLACE FUNCTION HR.ORDER_HAS_ITEM (
    p_order_id IN NUMBER,
    p_sku      IN VARCHAR2
) RETURN NUMBER AS
    v_exists NUMBER := 0;
BEGIN
    SELECT CASE WHEN EXISTS (
               SELECT 1
                 FROM HR.ORDER_ITEMS oi
                WHERE oi.order_id = p_order_id
                  AND oi.sku = p_sku
           ) THEN 1 ELSE 0 END
      INTO v_exists
      FROM DUAL;

    RETURN v_exists;
END;
/

-- Additional combinations: table-valued-like usage via SELECT FROM function (pipelined not required for sample)
-- and function call in WHERE clause
CREATE OR REPLACE PROCEDURE HR.REPORT_CUSTOMER_ORDERS (
    p_customer_id IN NUMBER
) AS
    v_has_item NUMBER;
BEGIN
    -- Function call in WHERE and SELECT list
    SELECT o.order_id,
           HR.TOTAL_AMOUNT(o.order_id) AS total_amount
      FROM HR.ORDERS o
     WHERE o.customer_id = p_customer_id
       AND HR.ORDER_HAS_ITEM(o.order_id, 'SKU-123') = 1;

    -- Direct proc call (no named args)
    HR.LOG_ACCESS(USER, 'REPORT_CUSTOMER_ORDERS');
END;
/

-- Procedure demonstrating IN, OUT, IN OUT parameters
CREATE OR REPLACE PROCEDURE HR.GET_ORDER_INFO (
    p_order_id IN NUMBER,
    p_total    OUT NUMBER,
    p_status   IN OUT VARCHAR2
) AS
BEGIN
    SELECT total_amount, status
      INTO p_total, p_status
      FROM HR.ORDERS
     WHERE order_id = p_order_id;

    p_status := p_status || '_PROCESSED';
END;
/

-- Function with default parameter value
CREATE OR REPLACE FUNCTION HR.CALC_DISCOUNT (
    p_amount IN NUMBER,
    p_rate   IN NUMBER DEFAULT 0.10
) RETURN NUMBER AS
BEGIN
    RETURN p_amount * (1 - p_rate);
END;
/

-- Anonymous block exercising different call styles
DECLARE
    v_total  NUMBER;
    v_status VARCHAR2(50) := 'NEW';
BEGIN
    -- Named notation
    HR.GET_ORDER_INFO(p_order_id => 1001,
                      p_total    => v_total,
                      p_status   => v_status);

    -- Positional notation
    HR.GET_ORDER_INFO(1002, v_total, v_status);

    -- Mixed and reordered named parameters
    HR.GET_ORDER_INFO(p_status  => v_status,
                      p_order_id => 1003,
                      p_total   => v_total);

    -- Function call with default parameter
    v_total := HR.CALC_DISCOUNT(100);

    -- Function call with explicit named parameter for rate
    v_total := HR.CALC_DISCOUNT(p_amount => 200, p_rate => 0.20);
END;
/
