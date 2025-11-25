-- PL/SQL routine variants: CREATE vs REPLACE, qualified vs unqualified calls

-- Plain CREATE PROCEDURE (no OR REPLACE)
CREATE PROCEDURE HR.UPDATE_ORDER_STATUS (
    p_order_id IN NUMBER,
    p_status   IN VARCHAR2
) AS
BEGIN
    -- Table reference: HR.ORDERS
    UPDATE HR.ORDERS
       SET status = p_status
     WHERE order_id = p_order_id;

    -- Unqualified procedure call in same schema
    LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS');

    -- Qualified procedure call in same schema
    HR.LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS_QUALIFIED');
END;
/

-- Plain CREATE PROCEDURE (no OR REPLACE), without schema
CREATE PROCEDURE UPDATE_ORDER_STATUS_NO_SCHEMA (
    p_order_id IN NUMBER,
    p_status   IN VARCHAR2
) AS
BEGIN
    -- Table reference: ORDERS (unqualified)
    UPDATE ORDERS
       SET status = p_status
     WHERE order_id = p_order_id;

    -- Qualified procedure call
    HR.LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS_NO_SCHEMA');

    -- Standalone call (procedure-style, discards return value)
    SIMPLE_TOTAL_NO_SCHEMA(100);
END;
/

-- CREATE OR REPLACE PROCEDURE, without schema
CREATE OR REPLACE PROCEDURE UPDATE_ORDER_STATUS_OR_REPLACE (
    p_order_id IN NUMBER,
    p_status   IN VARCHAR2
) AS
BEGIN
    -- Table reference: HR.ORDERS
    UPDATE HR.ORDERS
       SET status = p_status
     WHERE order_id = p_order_id;

    -- Unqualified procedure call
    LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS_OR_REPLACE');

    -- Qualified function call
    v_dummy := HR.SIMPLE_TOTAL(10);
END;
/

-- CREATE OR REPLACE PROCEDURE, schema-qualified
CREATE OR REPLACE PROCEDURE HR.UPDATE_ORDER_STATUS_OR_REPLACE_SCHEMA (
    p_order_id IN NUMBER,
    p_status   IN VARCHAR2
) AS
BEGIN
    -- Table reference: ORDERS (unqualified)
    UPDATE ORDERS
       SET status = p_status
     WHERE order_id = p_order_id;

    -- Procedure calls (standalone statements)
    LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS_OR_REPLACE_SCHEMA');
    HR.LOG_ACCESS(USER, 'UPDATE_ORDER_STATUS_OR_REPLACE_SCHEMA_Q');
    -- Standalone calls to functions (procedure-style, discards return value)
    SIMPLE_TOTAL_OR_REPLACE(20);
    HR.SIMPLE_TOTAL_OR_REPLACE_SCHEMA(30);
END;
/

-- Plain CREATE FUNCTION (no OR REPLACE), schema-qualified
CREATE FUNCTION HR.SIMPLE_TOTAL (
    p_amount IN NUMBER
)
RETURN NUMBER AS
    v_total NUMBER;
BEGIN
    SELECT SUM(amount)
      INTO v_total
      FROM HR.ORDERS;
    -- Function call in RETURN expression
    RETURN SIMPLE_TOTAL_NO_SCHEMA(v_total);
END;
/

-- Plain CREATE FUNCTION (no OR REPLACE), without schema
CREATE FUNCTION SIMPLE_TOTAL_NO_SCHEMA (
    p_amount IN NUMBER
) RETURN NUMBER AS
    v_total NUMBER;
BEGIN
    SELECT SUM(amount)
      INTO v_total
      FROM ORDERS;
    -- Qualified procedure call
    HR.LOG_ACCESS(USER, 'SIMPLE_TOTAL_NO_SCHEMA');
    RETURN v_total;
END;
/

-- CREATE OR REPLACE FUNCTION, without schema
CREATE OR REPLACE FUNCTION SIMPLE_TOTAL_OR_REPLACE (
    p_amount IN NUMBER
) RETURN NUMBER AS
    v_total NUMBER;
BEGIN
    SELECT SUM(amount)
      INTO v_total
      FROM HR.ORDERS;
    -- Function call in RETURN expression
    RETURN SIMPLE_TOTAL_NO_SCHEMA(v_total);
END;
/

-- CREATE OR REPLACE FUNCTION, schema-qualified
CREATE OR REPLACE FUNCTION HR.SIMPLE_TOTAL_OR_REPLACE_SCHEMA (
    p_amount IN NUMBER
) RETURN NUMBER AS
    v_total NUMBER;
BEGIN
    SELECT SUM(amount)
      INTO v_total
      FROM ORDERS;
    -- Function calls in RETURN expression (qualified and unqualified)
    RETURN HR.SIMPLE_TOTAL(v_total) + SIMPLE_TOTAL_OR_REPLACE(v_total);
END;
/


-- Anonymous block using qualified and unqualified routine calls
BEGIN
    -- Unqualified procedure call
    UPDATE_ORDER_STATUS(1001, 'SHIPPED');

    -- Qualified procedure call
    HR.UPDATE_ORDER_STATUS(1002, 'CANCELLED');

    -- Qualified & unqualified function calls in SELECT
    FOR rec IN (
        SELECT HR.SIMPLE_TOTAL(10)   AS total_qualified,
               SIMPLE_TOTAL(20)      AS total_unqualified
          FROM DUAL
    ) LOOP
        NULL; -- no-op
    END LOOP;
END;
/

-- ALTER statements for existing routines (recompile)
ALTER PROCEDURE HR.UPDATE_ORDER_STATUS COMPILE;
ALTER FUNCTION HR.SIMPLE_TOTAL COMPILE;

