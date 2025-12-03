# SQL Analysis

CodeFrame provides multi-dialect SQL parsing to extract structural metadata from SQL files. This document covers supported dialects, object types, extraction capabilities, and known limitations.

## Supported Dialects

| Dialect | Parser | Routines Support |
|---------|--------|------------------|
| **PostgreSQL** | JSqlParser | ✅ Full (dollar-quoted bodies; triggers via regex fallback) |
| **MySQL** | JSqlParser | ⚠️ Partial (bodies analyzed via preprocessing; triggers via regex fallback) |
| **T-SQL (MSSQL)** | ANTLR | ✅ Full |
| **PL/SQL (Oracle)** | ANTLR | ✅ Full |

### Dialect Detection

CodeFrame runs **dialect-agnostic** and does not require configuration. The parsing strategy is:

1. **JSqlParser first**: Attempts to parse the entire file using JSqlParser, which handles standard SQL and PostgreSQL well.
2. **ANTLR fallback for routines**: When JSqlParser cannot parse routine bodies (T-SQL, PL/SQL), CodeFrame detects dialect markers and delegates to the appropriate ANTLR grammar:
   - **T-SQL markers**: `CREATE OR ALTER`, `GO` batch separator, `EXEC`/`EXECUTE` statements
   - **PL/SQL markers**: `/` statement terminator, `CREATE OR REPLACE`, `BEGIN...END` blocks with trailing `/`

This hybrid approach maximizes compatibility without requiring user configuration.

---

## Supported Object Types

### Tables (`CREATE TABLE`, `ALTER TABLE`)

**Extracted metadata:**
- **Name**: Table name (e.g., `orders`)
- **Schema**: Optional schema qualifier (e.g., `sales.orders` → schema: `sales`)
- **Columns**: Name, data type, nullability
- **Constraints**:
  - Primary Key (inline and named)
  - Foreign Key (with referenced table and columns; ON DELETE/UPDATE actions are **not** extracted)
  - Unique (inline and named)
  - NOT NULL

**Example output** (from `constraints_and_indexes.sql`):
```json
{
  "createTables": [{
    "tableName": "users",
    "schema": "public",
    "ifNotExists": true,
    "columns": [
      {"name": "id", "type": "INT", "nullable": true, "constraints": ["PRIMARY KEY"]},
      {"name": "email", "type": "VARCHAR(255)", "nullable": false, "constraints": ["NOT NULL", "UNIQUE"]},
      {"name": "username", "type": "VARCHAR(50)", "nullable": false, "constraints": ["NOT NULL"]}
    ],
    "primaryKeys": ["id"],
    "foreignKeys": []
  }, {
    "tableName": "orders",
    "schema": "sales",
    "ifNotExists": true,
    "columns": [
      {"name": "order_id", "type": "INT", "nullable": false, "constraints": ["NOT NULL"]},
      {"name": "user_id", "type": "INT", "nullable": false, "constraints": ["NOT NULL"]}
    ],
    "primaryKeys": ["order_id", "user_id"],
    "foreignKeys": [{
      "name": "fk_orders_user",
      "columns": ["user_id"],
      "referencedTable": "public.users",
      "referencedColumns": ["id"]
    }]
  }]
}
```

**Notes:**
- `nullable` is only `false` when explicit `NOT NULL` is present (inline `PRIMARY KEY` does not imply non-null)
- FK `name` is captured when the constraint is named (e.g., `CONSTRAINT fk_orders_user FOREIGN KEY...`); `null` if unnamed
- Types are normalized to remove extra spaces: `VARCHAR(255)` not `VARCHAR (255)`

### ALTER TABLE

**Extracted metadata:**
- **Table name** and **Schema**
- **Operation type**: `ADD_COLUMN`, `DROP_COLUMN`, `ADD_CONSTRAINT`, `DROP_CONSTRAINT`
- **Added columns**: New column definitions
- **Dropped columns**: Column names removed
- **Added constraints**: Constraint definitions (as strings)
- **Dropped constraints**: Constraint names removed

**Example output** (from `alter_tables_views.sql`):
```json
{
  "alterTables": [{
    "tableName": "users",
    "schema": "public",
    "operationType": "ADD_COLUMN",
    "addedColumns": [{
      "name": "last_login",
      "type": "TIMESTAMP",
      "nullable": true,
      "constraints": []
    }],
    "droppedColumns": [],
    "modifiedColumns": [],
    "addedConstraints": [],
    "droppedConstraints": []
  }, {
    "tableName": "orders",
    "schema": "sales",
    "operationType": "ADD_CONSTRAINT",
    "addedColumns": [],
    "droppedColumns": [],
    "modifiedColumns": [],
    "addedConstraints": ["CONSTRAINT pk_orders PRIMARY KEY (order_id, user_id)"],
    "droppedConstraints": []
  }, {
    "tableName": "orders",
    "schema": "sales",
    "operationType": "DROP_COLUMN",
    "addedColumns": [],
    "droppedColumns": ["total_amount"],
    "modifiedColumns": [],
    "addedConstraints": [],
    "droppedConstraints": []
  }]
}
```

### Views (`CREATE VIEW`, `ALTER VIEW`)

**Extracted metadata:**
- **Name** and **Schema**
- **References**: Tables/views used in the view definition (FROM/JOIN clauses)

**Example output** (from `constraints_and_indexes.sql`):
```json
{
  "createViews": [{
    "viewName": "v_orders",
    "schema": "sales",
    "orReplace": false,
    "references": {
      "relations": ["public.users", "sales.orders"]
    }
  }]
}
```

### Indexes (`CREATE INDEX`)

**Extracted metadata:**
- **Name** and **Schema**
- **Table**: The table being indexed
- **Columns**: Indexed column list
- **Unique**: Whether the index is unique

**Example output** (from `constraints_and_indexes.sql`):
```json
{
  "createIndexes": [{
    "indexName": "ix_orders_date_amount",
    "tableName": "orders",
    "schema": "sales",
    "unique": false,
    "columns": ["order_date", "total_amount"],
    "indexType": null
  }, {
    "indexName": "ux_users_email",
    "tableName": "users",
    "schema": "public",
    "unique": true,
    "columns": ["email"],
    "indexType": null
  }]
}
```

### Procedures (`CREATE PROCEDURE`)

**Extracted metadata:**
- **Name** and **Schema**
- **Parameters**: Name, type, mode (IN/OUT/INOUT)
- **References**: Tables/views accessed in the body
- **Calls**: Functions and procedures invoked in the body

**Example output** (from `procedures_functions.sql`):
```json
{
  "createProcedures": [{
    "procedureName": "recalc_order_total",
    "schema": "sales",
    "orReplace": false,
    "parameters": [
      {"name": "p_order_id", "type": "INT", "direction": "IN"}
    ],
    "references": {
      "relations": ["sales.orders"]
    },
    "calls": {
      "functions": ["sales.get_total"],
      "procedures": ["sales.notify_order_update"]
    }
  }]
}
```

### Functions (`CREATE FUNCTION`)

**Extracted metadata:**
- **Name** and **Schema**
- **Parameters**: Name, type, mode, default value (if any)
- **Return Type**
- **References**: Tables/views accessed in the body
- **Calls**: Functions invoked in the body

**Example output** (from `procedures_functions.sql`):
```json
{
  "createFunctions": [{
    "functionName": "get_total",
    "schema": "sales",
    "orReplace": false,
    "parameters": [
      {"name": "order_id", "type": "INT", "direction": null}
    ],
    "returnType": "DECIMAL(12,2)",
    "references": {
      "relations": ["sales.order_lines"]
    },
    "calls": {
      "functions": ["COALESCE", "SUM"],
      "procedures": []
    }
  }]
}
```

**Notes:**
- `direction` is `null` for PostgreSQL functions parsed via JSqlParser (direction is PL/SQL/T-SQL specific)
- Types are normalized to consistent format: `DECIMAL(12,2)` (no extra spaces)
- PostgreSQL: References inside `IF EXISTS(SELECT...)` clauses are captured

### Triggers (`CREATE TRIGGER`)

**Extracted metadata:**
- **Name** and **Schema**
- **Table**: The table the trigger is attached to (or `DATABASE`/`SCHEMA` for DDL triggers)
- **Timing**: BEFORE, AFTER, INSTEAD OF, COMPOUND (PL/SQL)
- **Events**: INSERT, UPDATE, DELETE (can be multiple); DDL events for DDL triggers
- **orReplace**: Whether `CREATE OR REPLACE` was used
- **References**: Tables/views accessed in the trigger body
- **Calls**: Functions and procedures invoked in the trigger body

**Example output** (from `triggers_tsql.sql`):
```json
{
  "createTriggers": [{
    "triggerName": "tr_orders_insert",
    "tableName": "dbo.orders",
    "schema": "dbo",
    "orReplace": false,
    "timing": "AFTER",
    "events": ["INSERT"],
    "references": {
      "relations": ["dbo.audit_log"]
    },
    "calls": {
      "functions": ["GETDATE"],
      "procedures": []
    }
  }, {
    "triggerName": "tr_customers_audit",
    "tableName": "dbo.customers",
    "schema": "dbo",
    "orReplace": true,
    "timing": "AFTER",
    "events": ["UPDATE", "DELETE"],
    "references": {
      "relations": ["dbo.customer_history"]
    },
    "calls": {
      "functions": ["GETDATE"],
      "procedures": []
    }
  }]
}
```

**Dialect support:**
- **T-SQL**: ✅ Full (ANTLR). DML triggers (`ON table`) and DDL triggers (`ON DATABASE`/`ON ALL SERVER`). `FOR` is treated as `AFTER`.
- **PL/SQL**: ✅ Full (ANTLR). Simple DML triggers, compound triggers, and DDL triggers (`ON SCHEMA`/`ON DATABASE`).
- **PostgreSQL**: ⚠️ Partial (regex fallback). Triggers with `EXECUTE FUNCTION`/`PROCEDURE` syntax are extracted.
- **MySQL**: ⚠️ Partial (regex fallback). Inline trigger bodies (`BEGIN...END`, including `DELIMITER $$` blocks) and single-statement triggers are analyzed for references and calls.

### Drop Operations

All `DROP` statements are recorded:

```json
{
  "dropOperations": [
    {"objectType": "TABLE", "objectName": "legacy_users", "schema": "public", "ifExists": true},
    {"objectType": "VIEW", "objectName": "v_old_orders", "schema": "sales", "ifExists": false},
    {"objectType": "INDEX", "objectName": "ix_old_index", "schema": "sales", "ifExists": true}
  ]
}
```

---

## Top-Level Statements

Standalone statements (outside any routine definition) are captured separately:

- **topLevelReferences**: Tables/views referenced by top-level SELECT/INSERT/UPDATE/DELETE
- **topLevelCalls**: Functions and procedures called at file scope

**Supported top-level constructs:**
- CALL/EXEC statements
- Standalone SELECT/INSERT/UPDATE/DELETE
- PL/SQL anonymous blocks (`BEGIN...END`)
- EXECUTE statements (PL/SQL)

**Example:**
```sql
-- Top-level procedure call
CALL sales.recalc_order_total(123);

-- Top-level SELECT
SELECT sales.get_total(o.id) FROM sales.orders o;
```

**Output** (from `top_level_statements.sql`):
```json
{
  "topLevelReferences": {
    "relations": ["sales.orders", "sales.order_summaries"]
  },
  "topLevelCalls": {
    "functions": ["COALESCE", "sales.get_total", "SUM"],
    "procedures": ["sales.recalc_order_total"]
  }
}
```

**Note:** Built-in aggregate functions (like `SUM`, `COALESCE`) are also captured in `topLevelCalls.functions`.

**PL/SQL Anonymous Block Example** (from `plsql_routine_variants.sql`):
```sql
BEGIN
    UPDATE_ORDER_STATUS(1001, 'SHIPPED');
    HR.UPDATE_ORDER_STATUS(1002, 'CANCELLED');
    FOR rec IN (SELECT HR.SIMPLE_TOTAL(10) FROM DUAL) LOOP
        NULL;
    END LOOP;
END;
/
```

**Output:**
```json
{
  "topLevelReferences": {"relations": ["DUAL"]},
  "topLevelCalls": {
    "functions": ["HR.SIMPLE_TOTAL", "SIMPLE_TOTAL"],
    "procedures": ["UPDATE_ORDER_STATUS", "HR.UPDATE_ORDER_STATUS"]
  }
}
```

---

## PL/SQL Packages

PL/SQL packages (specification and body) are partially supported:

- Package body routines are extracted as regular procedures/functions
- Routine names include the package name: `PACKAGE_NAME.ROUTINE_NAME`
- Both qualified (`HR.ORDER_PKG.GET_TOTAL`) and unqualified (`GET_TOTAL`) calls are captured

**Note**: There are no separate `createPackages` or `createPackageBodies` arrays. Package routines appear in `createProcedures` and `createFunctions` with compound names.

**Example output** (from `plsql_packages.sql`):
```json
{
  "createFunctions": [{
    "functionName": "ORDER_PKG.GET_TOTAL",
    "schema": "HR",
    "orReplace": false,
    "parameters": [{"name": "p_order_id", "type": "NUMBER", "direction": "IN"}],
    "returnType": "NUMBER",
    "references": {"relations": ["HR.ORDER_ITEMS"]},
    "calls": {"functions": [], "procedures": []}
  }],
  "createProcedures": [{
    "procedureName": "ORDER_PKG.LOG_ORDER",
    "schema": "HR",
    "orReplace": false,
    "parameters": [
      {"name": "p_order_id", "type": "NUMBER", "direction": "IN"},
      {"name": "p_action", "type": "VARCHAR2", "direction": "IN"}
    ],
    "references": {"relations": ["HR.ORDER_LOG"]},
    "calls": {"functions": [], "procedures": []}
  }],
  "topLevelCalls": {
    "functions": [],
    "procedures": ["ORDER_PKG.PROCESS", "HR.ORDER_PKG.PROCESS"]
  }
}
```

**Note:** Anonymous block calls (from `BEGIN...END` at file level) are captured in `topLevelCalls`.

---

## Dialect-Specific Notes

### T-SQL (SQL Server)

- **Batch separator**: `GO` statements are recognized and used to split batches
- **Parameters**: `@`-prefixed parameters are extracted with the `@` prefix preserved
- **EXEC/EXECUTE**: Both `EXEC proc` and `EXECUTE proc` are captured as procedure calls
- **CREATE OR ALTER**: Treated as create with `orReplace: true`
- **Table-valued functions**: `RETURNS TABLE` return type is captured

**Example output** (from `tsql_routine_bodies.sql`):
```json
{
  "createProcedures": [{
    "procedureName": "usp_GetOrdersByCustomer",
    "schema": "dbo",
    "orReplace": true,
    "parameters": [{"name": "@CustomerId", "type": "INT", "direction": "IN"}],
    "references": {"relations": ["dbo.OrderItems", "dbo.Orders"]},
    "calls": {
      "functions": ["dbo.ufn_TotalAmount"],
      "procedures": ["dbo.usp_LogAccess"]
    }
  }],
  "createFunctions": [{
    "functionName": "ufn_TotalAmount",
    "schema": "dbo",
    "orReplace": true,
    "parameters": [{"name": "@OrderId", "type": "INT", "direction": "IN"}],
    "returnType": "DECIMAL(18,2)",
    "references": {"relations": ["dbo.OrderItems"]},
    "calls": {"functions": [], "procedures": []}
  }]
}
```

### PL/SQL (Oracle)

- **Statement terminator**: `/` on its own line terminates PL/SQL blocks
- **Anonymous blocks**: `DECLARE...BEGIN...END` blocks are analyzed for references and calls
- **EXECUTE**: `EXECUTE schema.proc(...)` is captured as a top-level procedure call
- **Named notation**: `proc(p_id => 123)` parameter passing is handled
- **REPLACE**: Both `CREATE OR REPLACE` and standalone `REPLACE` are supported

**Example output** (from `plsql_routine_variants.sql`):
```json
{
  "createProcedures": [{
    "procedureName": "UPDATE_ORDER_STATUS",
    "schema": "HR",
    "orReplace": false,
    "parameters": [
      {"name": "p_order_id", "type": "NUMBER", "direction": "IN"},
      {"name": "p_status", "type": "VARCHAR2", "direction": "IN"}
    ],
    "references": {"relations": ["HR.ORDERS"]},
    "calls": {
      "functions": [],
      "procedures": ["HR.LOG_ACCESS", "LOG_ACCESS"]
    }
  }]
}
```

**Note:** Both qualified (`HR.LOG_ACCESS`) and unqualified (`LOG_ACCESS`) calls are captured separately.

### PostgreSQL

- **Dollar-quoting**: `$$...$$` and `$tag$...$tag$` body delimiters are fully supported
- **LANGUAGE clause**: `LANGUAGE SQL`, `LANGUAGE plpgsql` are recognized
- **CREATE OR REPLACE**: Treated as create operation (body analyzed for references)
- **SET-RETURNING functions**: `RETURNS TABLE(...)` return types are captured
- **CALL statement**: PostgreSQL 11+ `CALL procedure(...)` is supported

### MySQL

- **DELIMITER**: Custom delimiters (`DELIMITER $$`) are recognized for parsing multi-statement routines
- **Routine bodies**: Bodies are preprocessed to strip control-flow and normalize assignments so that DML statements and calls can be analyzed; complex procedural logic may still be partially ignored.
- **Triggers**: DML triggers on tables are extracted via regex (including `DELIMITER $$` blocks and single-statement triggers), and their bodies are analyzed for table references and function/procedure calls.
- **DETERMINISTIC/READS SQL DATA**: Routine characteristics are captured where possible

---

## Limitations

### General

- **References are relations only**: We do not distinguish tables from views without a project-wide catalog. Each operation exposes `references.relations`.
- **Calls use explicit AST nodes**: Functions are captured from explicit function call nodes; procedures from `CALL`/`EXEC` statements.

### PostgreSQL

- **ALTER FUNCTION metadata-only**: Statements like `ALTER FUNCTION ... RENAME TO ...` or `OWNER TO ...` are not parsed. PostgreSQL uses `CREATE OR REPLACE` for body changes.

### MySQL

- **BEGIN...END body parsing**: The parser often marks MySQL-style routine bodies as errors. Header information (name, parameters, return type) is extracted, but body references/calls may be incomplete.
- **DELIMITER handling**: The delimiter change is recognized, but complex delimiter scenarios may not parse correctly.

### T-SQL

- **GO positioning**: The `GO` batch separator must appear on its own line to be recognized.

### PL/SQL

- **Standalone top-level SELECT**: A bare `SELECT ... FROM ...` statement (outside an anonymous block) is not analyzed. Wrap in `BEGIN...END` if analysis is needed.
- **`REPLACE FUNCTION/PROCEDURE`** (without `CREATE`): Not supported. The grammar requires `CREATE` or `CREATE OR REPLACE`. Standalone `REPLACE` is a rare Oracle syntax variant.
- **Package private routines**: Routines declared only in the package body (not in spec) are captured during body analysis.

### Not Extracted

The following are currently **not extracted**:
- CHECK constraints
- DEFAULT constraints/values
- Computed/generated columns
- Sequences
- User-defined types
- Synonyms
- Materialized views
- Database links

---

## Output Structure

Each `.sql` file produces a JSON object with these fields:

```json
{
  "filePath": "path/to/file.sql",
  "language": "sql",
  
  "topLevelReferences": { "relations": [...] },
  "topLevelCalls": { "functions": [...], "procedures": [...] },
  
  "createTables": [...],
  "alterTables": [...],
  "createViews": [...],
  "alterViews": [...],
  "createIndexes": [...],
  "createProcedures": [...],
  "alterProcedures": [...],
  "createFunctions": [...],
  "alterFunctions": [...],
  "createTriggers": [...],
  "dropOperations": [...]
}
```

Empty arrays are omitted from output for brevity.

---

## Test Samples

Sample SQL files covering various scenarios are located in:

```
src/test/resources/samples/sql/
```

| Sample File | Coverage |
|-------------|----------|
| `sample.sql` | Basic tables, views, indexes, MySQL-style procedure/function |
| `constraints_and_indexes.sql` | PK, FK (unnamed), UNIQUE, multi-column indexes, schema-qualified names |
| `procedures_functions.sql` | PostgreSQL dollar-quoted routines, function calls, procedure calls |
| `tsql_routine_bodies.sql` | T-SQL CREATE OR ALTER, @-prefixed parameters, EXEC calls |
| `plsql_routine_bodies.sql` | PL/SQL procedures, functions, IN/OUT/INOUT parameters |
| `plsql_packages.sql` | PL/SQL package body routines, anonymous block calls |
| `plsql_routine_variants.sql` | CREATE vs CREATE OR REPLACE, qualified/unqualified calls |
| `alter_tables_views.sql` | ALTER TABLE ADD/DROP COLUMN, ADD CONSTRAINT, ALTER VIEW |
| `alter_routines_tsql.sql` | T-SQL ALTER PROCEDURE/FUNCTION with body analysis |
| `alter_routines_postgresql.sql` | PostgreSQL CREATE OR REPLACE (body changes) |
| `alter_routines_mysql.sql` | MySQL routine definitions with DELIMITER |
| `top_level_statements.sql` | PostgreSQL top-level CALL and SELECT |
| `top_level_tsql.sql` | T-SQL EXEC and top-level SELECT with UDF |
| `top_level_plsql.sql` | PL/SQL anonymous blocks, EXECUTE statement |
| `top_level_mysql.sql` | MySQL CALL and top-level SELECT |
| `drop_statements.sql` | DROP TABLE/VIEW/INDEX with IF EXISTS |
| `triggers_tsql.sql` | T-SQL DML and DDL triggers, INSTEAD OF triggers |
| `triggers_plsql.sql` | PL/SQL DML triggers, compound triggers, DDL triggers |
| `triggers_postgresql.sql` | PostgreSQL triggers with EXECUTE FUNCTION/PROCEDURE |
| `triggers_mysql.sql` | MySQL triggers with inline BEGIN...END bodies |

