-- PL/SQL packages sample: package spec/body + qualified and unqualified calls

-- Package specification
CREATE OR REPLACE PACKAGE HR.ORDER_PKG AS
    FUNCTION GET_TOTAL(p_order_id NUMBER) RETURN NUMBER;
    PROCEDURE LOG_ORDER(p_order_id NUMBER, p_action VARCHAR2);
    PROCEDURE PROCESS(p_order_id NUMBER);
    PROCEDURE PROCESS(p_order_id NUMBER, p_priority VARCHAR2);
END ORDER_PKG;
/

-- Package body
CREATE OR REPLACE PACKAGE BODY HR.ORDER_PKG AS

    FUNCTION GET_TOTAL(p_order_id NUMBER) RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        -- Table reference: HR.ORDER_ITEMS
        SELECT SUM(quantity * price)
          INTO v_total
          FROM HR.ORDER_ITEMS
         WHERE order_id = p_order_id;
        RETURN v_total;
    END GET_TOTAL;

    PROCEDURE LOG_ORDER(p_order_id NUMBER, p_action VARCHAR2) IS
    BEGIN
        -- Table reference: HR.ORDER_LOG
        INSERT INTO HR.ORDER_LOG(order_id, action, created_at)
        VALUES (p_order_id, p_action, SYSDATE);
    END LOG_ORDER;

    PROCEDURE PROCESS(p_order_id NUMBER) IS
        v_total NUMBER;
    BEGIN
        -- Qualified function call inside package
        v_total := HR.ORDER_PKG.GET_TOTAL(p_order_id);

        -- Unqualified procedure call inside package
        LOG_ORDER(p_order_id, 'PROCESS_DEFAULT');
    END PROCESS;

    PROCEDURE PROCESS(p_order_id NUMBER, p_priority VARCHAR2) IS
    BEGIN
        -- Overload calling other overload
        PROCESS(p_order_id);

        -- Qualified procedure call with different action
        HR.ORDER_PKG.LOG_ORDER(p_order_id, 'PROCESS_' || p_priority);
    END PROCESS;

END ORDER_PKG;
/

-- Anonymous block calling packaged routines
BEGIN
    -- Qualified calls
    HR.ORDER_PKG.PROCESS(1001);
    HR.ORDER_PKG.PROCESS(1002, 'HIGH');

    -- Unqualified calls (same schema context)
    ORDER_PKG.PROCESS(1003);
    ORDER_PKG.PROCESS(1004, 'LOW');
END;
/
