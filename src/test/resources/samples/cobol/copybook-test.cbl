       IDENTIFICATION DIVISION.
       PROGRAM-ID. COPYBOOK-TEST.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY SIMPLE.
       
       01  FUNCTION-PARAMETERS.
           05  FUNC-INPUT      PIC X(20).
           05  FUNC-OUTPUT     PIC X(20).
           05  FUNC-RESULT     PIC 9(3).

       01  FUNCTION-ERRORS.
           05  ERROR-CODE      PIC 9(2).
           
       01 WS-RESULT PIC 9(5).

       PROCEDURE DIVISION.
           COPY PROCEDURES.
