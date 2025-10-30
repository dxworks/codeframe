-- Composite PK, FK with actions, unique & multi-column indexes, schema-qualified names
CREATE TABLE IF NOT EXISTS public.users (
    id INT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS sales.orders (
    order_id INT NOT NULL,
    user_id INT NOT NULL,
    order_date DATE,
    total_amount DECIMAL(12, 2),
    CONSTRAINT pk_orders PRIMARY KEY (order_id, user_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
        ON UPDATE NO ACTION
);

-- Multi-column index
CREATE INDEX ix_orders_date_amount ON sales.orders(order_date, total_amount);

-- Unique index
CREATE UNIQUE INDEX ux_users_email ON public.users(email);

-- View referencing multiple tables
CREATE VIEW sales.v_orders AS
SELECT o.order_id, o.user_id, u.email, o.order_date
FROM sales.orders o
JOIN public.users u ON u.id = o.user_id;
