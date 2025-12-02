-- T-SQL Trigger Examples
-- Tests DML triggers, DDL triggers, and various trigger options

-- Simple AFTER INSERT trigger
CREATE TRIGGER dbo.tr_orders_insert
ON dbo.orders
AFTER INSERT
AS
BEGIN
    INSERT INTO dbo.audit_log (table_name, action, created_at)
    SELECT 'orders', 'INSERT', GETDATE()
    FROM inserted;
END;
GO

-- AFTER UPDATE, DELETE trigger (multiple events)
CREATE OR ALTER TRIGGER dbo.tr_customers_audit
ON dbo.customers
AFTER UPDATE, DELETE
AS
BEGIN
    IF EXISTS (SELECT 1 FROM deleted)
    BEGIN
        INSERT INTO dbo.customer_history (customer_id, action, changed_at)
        SELECT customer_id, 
               CASE WHEN EXISTS (SELECT 1 FROM inserted) THEN 'UPDATE' ELSE 'DELETE' END,
               GETDATE()
        FROM deleted;
    END
END;
GO

-- INSTEAD OF trigger on view
CREATE TRIGGER sales.tr_v_order_summary_insert
ON sales.v_order_summary
INSTEAD OF INSERT
AS
BEGIN
    INSERT INTO sales.orders (customer_id, order_date, total_amount)
    SELECT customer_id, order_date, total_amount
    FROM inserted;
    
    EXEC dbo.usp_notify_new_order;
END;
GO

-- DDL trigger on DATABASE
CREATE TRIGGER tr_ddl_audit
ON DATABASE
AFTER CREATE_TABLE, ALTER_TABLE, DROP_TABLE
AS
BEGIN
    INSERT INTO dbo.ddl_audit_log (event_type, object_name, event_date)
    SELECT EVENTDATA().value('(/EVENT_INSTANCE/EventType)[1]', 'NVARCHAR(100)'),
           EVENTDATA().value('(/EVENT_INSTANCE/ObjectName)[1]', 'NVARCHAR(256)'),
           GETDATE();
END;
GO

-- Trigger with function call in body
CREATE TRIGGER hr.tr_employees_salary_check
ON hr.employees
AFTER INSERT, UPDATE
AS
BEGIN
    DECLARE @max_salary DECIMAL(18,2);
    SET @max_salary = hr.fn_get_max_salary();
    
    IF EXISTS (SELECT 1 FROM inserted WHERE salary > @max_salary)
    BEGIN
        RAISERROR('Salary exceeds maximum allowed', 16, 1);
        ROLLBACK TRANSACTION;
    END
END;
GO
