-- Sample ALTER TABLE statements
ALTER TABLE public.users
  ADD COLUMN last_login TIMESTAMP;

ALTER TABLE sales.orders
  ADD CONSTRAINT pk_orders PRIMARY KEY (order_id, user_id);

ALTER TABLE sales.orders
  ADD CONSTRAINT fk_orders_user FOREIGN KEY (user_id)
  REFERENCES public.users(id)
  ON DELETE CASCADE
  ON UPDATE NO ACTION;

ALTER TABLE sales.orders
  DROP COLUMN total_amount;
