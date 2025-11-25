-- T-SQL routine bodies sample for analyzer tests

GO

CREATE OR ALTER PROCEDURE dbo.usp_GetOrdersByCustomer
    @CustomerId INT
AS
BEGIN
    SELECT o.OrderID, o.OrderDate, dbo.ufn_TotalAmount(o.OrderID) AS TotalAmount
    FROM dbo.Orders o
    JOIN dbo.OrderItems i ON i.OrderID = o.OrderID
    WHERE o.CustomerID = @CustomerId;

    EXEC dbo.usp_LogAccess @Who = SYSTEM_USER, @What = 'usp_GetOrdersByCustomer';
END
GO

CREATE OR ALTER FUNCTION dbo.ufn_TotalAmount(@OrderId INT)
RETURNS DECIMAL(18,2)
AS
BEGIN
    DECLARE @total DECIMAL(18,2);
    SELECT @total = SUM(i.Quantity * i.Price)
    FROM dbo.OrderItems i
    WHERE i.OrderID = @OrderId;
    RETURN @total;
END
GO

CREATE OR ALTER FUNCTION dbo.ufn_OrderHasItem(@OrderId INT, @Sku NVARCHAR(50))
RETURNS BIT
AS
BEGIN
    RETURN CASE WHEN EXISTS (
        SELECT 1 FROM dbo.OrderItems oi WHERE oi.OrderID = @OrderId AND oi.Sku = @Sku
    ) THEN 1 ELSE 0 END;
END
GO
