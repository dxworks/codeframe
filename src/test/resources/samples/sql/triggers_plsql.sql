-- PL/SQL Trigger Examples
-- Tests DML triggers, compound triggers, and DDL triggers

-- Simple BEFORE INSERT trigger
CREATE OR REPLACE TRIGGER hr.trg_employees_bi
BEFORE INSERT ON hr.employees
FOR EACH ROW
BEGIN
    :NEW.created_at := SYSDATE;
    :NEW.created_by := USER;
    
    INSERT INTO hr.audit_log (table_name, action, created_at)
    VALUES ('employees', 'INSERT', SYSDATE);
END;
/

-- AFTER UPDATE trigger with multiple columns
CREATE TRIGGER hr.trg_employees_salary_au
AFTER UPDATE OF salary, department_id ON hr.employees
FOR EACH ROW
BEGIN
    IF :OLD.salary != :NEW.salary THEN
        INSERT INTO hr.salary_history (employee_id, old_salary, new_salary, change_date)
        VALUES (:OLD.employee_id, :OLD.salary, :NEW.salary, SYSDATE);
    END IF;
    
    hr.pkg_notifications.send_update_alert(:NEW.employee_id);
END;
/

-- INSTEAD OF trigger on view
CREATE OR REPLACE TRIGGER sales.trg_v_orders_ioi
INSTEAD OF INSERT ON sales.v_order_details
FOR EACH ROW
BEGIN
    INSERT INTO sales.orders (order_id, customer_id, order_date)
    VALUES (:NEW.order_id, :NEW.customer_id, :NEW.order_date);
    
    INSERT INTO sales.order_items (order_id, product_id, quantity, price)
    VALUES (:NEW.order_id, :NEW.product_id, :NEW.quantity, :NEW.price);
END;
/

-- Compound DML trigger
CREATE OR REPLACE TRIGGER hr.trg_employees_compound
FOR INSERT OR UPDATE OR DELETE ON hr.employees
COMPOUND TRIGGER
    TYPE t_emp_ids IS TABLE OF hr.employees.employee_id%TYPE;
    g_emp_ids t_emp_ids := t_emp_ids();
    
BEFORE STATEMENT IS
BEGIN
    g_emp_ids.DELETE;
END BEFORE STATEMENT;

AFTER EACH ROW IS
BEGIN
    IF INSERTING OR UPDATING THEN
        g_emp_ids.EXTEND;
        g_emp_ids(g_emp_ids.LAST) := :NEW.employee_id;
    END IF;
END AFTER EACH ROW;

AFTER STATEMENT IS
BEGIN
    FOR i IN 1..g_emp_ids.COUNT LOOP
        hr.process_employee_change(g_emp_ids(i));
    END LOOP;
END AFTER STATEMENT;
END trg_employees_compound;
/

-- DDL trigger on SCHEMA
CREATE OR REPLACE TRIGGER trg_schema_ddl
AFTER CREATE OR ALTER OR DROP ON hr.SCHEMA
BEGIN
    INSERT INTO hr.ddl_log (event_type, object_name, ddl_time, username)
    VALUES (ORA_SYSEVENT, ORA_DICT_OBJ_NAME, SYSDATE, ORA_LOGIN_USER);
END;
/

-- Trigger calling function
CREATE TRIGGER sales.trg_orders_total
AFTER INSERT ON sales.order_items
FOR EACH ROW
BEGIN
    UPDATE sales.orders
    SET total_amount = sales.fn_calculate_order_total(:NEW.order_id)
    WHERE order_id = :NEW.order_id;
END;
/
