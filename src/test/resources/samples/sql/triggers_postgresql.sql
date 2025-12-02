-- PostgreSQL Trigger Examples
-- Tests BEFORE/AFTER triggers with EXECUTE FUNCTION syntax

-- Simple BEFORE INSERT trigger
CREATE TRIGGER trg_users_created_at
BEFORE INSERT ON public.users
FOR EACH ROW
EXECUTE FUNCTION public.set_created_timestamp();

-- AFTER UPDATE trigger
CREATE TRIGGER trg_orders_audit
AFTER UPDATE ON sales.orders
FOR EACH ROW
EXECUTE FUNCTION sales.log_order_changes();

-- Multiple events trigger
CREATE TRIGGER trg_products_history
AFTER INSERT OR UPDATE OR DELETE ON inventory.products
FOR EACH ROW
EXECUTE FUNCTION inventory.track_product_changes();

-- INSTEAD OF trigger on view
CREATE TRIGGER trg_v_customer_orders
INSTEAD OF INSERT ON sales.v_customer_orders
FOR EACH ROW
EXECUTE FUNCTION sales.insert_customer_order();

-- OR REPLACE trigger
CREATE OR REPLACE TRIGGER trg_employees_salary
BEFORE UPDATE ON hr.employees
FOR EACH ROW
EXECUTE FUNCTION hr.validate_salary_change();

-- Trigger with EXECUTE PROCEDURE (older syntax)
CREATE TRIGGER trg_audit_log
AFTER INSERT ON audit.events
FOR EACH ROW
EXECUTE PROCEDURE audit.process_event();

-- Statement-level trigger (no FOR EACH ROW)
CREATE TRIGGER trg_table_modified
AFTER INSERT ON config.settings
EXECUTE FUNCTION config.notify_settings_change();
