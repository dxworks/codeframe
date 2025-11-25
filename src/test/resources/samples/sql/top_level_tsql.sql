-- T-SQL top-level statements sample

-- Top-level procedure call
EXEC dbo.usp_RecalcOrderTotal @OrderId = 123;

-- Top-level SELECT with scalar UDF and table refs
SELECT dbo.ufn_TotalAmount(o.OrderID)
FROM dbo.Orders o
WHERE EXISTS (
    SELECT 1
    FROM dbo.OrderSummaries s
    WHERE s.OrderID = o.OrderID
);
