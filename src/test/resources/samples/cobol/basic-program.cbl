       IDENTIFICATION DIVISION.
       PROGRAM-ID. HELLO-WORLD.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-NAME PIC X(20).
       01  WS-TABLE.
           05  WS-ROW OCCURS 10 TIMES.
               10  WS-COL OCCURS 5 TIMES PIC 9(3).
       01  WS-INDEX1 PIC 9(2).
       01  WS-INDEX2 PIC 9(2).
       01  WS-FIRST-NAME PIC X(15).
       01  WS-MIDDLE-NAME PIC X(15).
       01  WS-LAST-NAME PIC X(20).
       01  WS-FULL-NAME PIC X(50).
       01  WS-STATUS-CODE PIC 9.
       01  WS-TRANSACTION-TABLE.
           05  WS-TRAN-REC OCCURS 100 TIMES.
               10  WS-TRAN-ID PIC 9(5).
               10  WS-TRAN-AMT PIC 9(8)V99.
       01  WS-TRAN-COUNTER PIC 9(3).

       PROCEDURE DIVISION.
       1000-MAIN.
           MOVE "HELLO" TO WS-NAME
           DISPLAY WS-NAME
           
           MOVE 1 TO WS-INDEX1
           MOVE 2 TO WS-INDEX2
           
           MOVE 100 TO WS-COL(WS-INDEX1, WS-INDEX2)
           ADD 50 TO WS-COL(WS-INDEX1, WS-INDEX2)
           
           COMPUTE WS-COL(WS-INDEX1, WS-INDEX2) = 
                   WS-COL(WS-INDEX1, WS-INDEX2) + 25
           
      *    STRING statement test
           STRING "HELLO" INTO WS-FULL-NAME
           STRING WS-FIRST-NAME DELIMITED BY SPACE
                  WS-MIDDLE-NAME DELIMITED BY SPACE  
                  WS-LAST-NAME DELIMITED BY SPACE
                  INTO WS-FULL-NAME
           
      *    EVALUATE statement test
           EVALUATE WS-STATUS-CODE
               WHEN 0 DISPLAY "Success"
               WHEN 1 DISPLAY "Warning"
               WHEN OTHER DISPLAY "Error"
           END-EVALUATE
           
      *    INITIALIZE statement test
           INITIALIZE WS-TRANSACTION-TABLE
           INITIALIZE WS-FIRST-NAME WS-LAST-NAME
           
      *    Additional DISPLAY with identifier operands
           DISPLAY WS-FULL-NAME
           DISPLAY WS-TRAN-COUNTER
           
           STOP RUN.
