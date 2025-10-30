-- Cross-dialect functions sample

-- MySQL-style function (no schema), BEGIN...END body
CREATE FUNCTION calc_tax(amount DECIMAL(10,2)) RETURNS DECIMAL(10,2) DETERMINISTIC
BEGIN
  DECLARE rate DECIMAL(5,2);
  -- Reference a table to fetch current tax rate
  SELECT rate INTO rate FROM shop.tax_rates WHERE active = 1 LIMIT 1;
  RETURN amount * rate;
END;

