       IDENTIFICATION DIVISION.
       PROGRAM-ID. FILEOPS-DEMO.
       AUTHOR.        JOHNDOE.
      ******************************************************************
      * Program     : FILEOPS-DEMO.CBL
      * Function    : Demonstrate all COBOL file operation verbs
      ******************************************************************

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CUSTOMER-FILE 
               ASSIGN TO CUSTFILE
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL.
           SELECT TRANSACTION-FILE 
               ASSIGN TO TRNXFILE
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS TRNX-ID.
           SELECT REPORT-FILE 
               ASSIGN TO RPTFILE
               ORGANIZATION IS SEQUENTIAL
               ACCESS MODE IS SEQUENTIAL.

       DATA DIVISION.
       FILE SECTION.
       FD  CUSTOMER-FILE.
       01  CUSTOMER-HEADER-RECORD.
           05  RECORD-TYPE     PIC X(1).
           05  HEADER-DATE     PIC X(8).
           05  RECORD-COUNT    PIC 9(5).
       01  CUSTOMER-DETAIL-RECORD.
           05  RECORD-TYPE     PIC X(1).
           05  CUST-ID         PIC X(10).
           05  CUST-NAME       PIC X(50).
           05  CUST-ADDRESS    PIC X(100).
       01  CUSTOMER-TRAILER-RECORD.
           05  RECORD-TYPE     PIC X(1).
           05  TRAILER-COUNT   PIC 9(5).

       FD  TRANSACTION-FILE.
       01  TRANSACTION-RECORD.
           05  TRNX-ID        PIC X(15).
           05  TRNX-AMOUNT    PIC 9(9)V99.
           05  TRNX-DATE      PIC X(8).
       01  TRANSACTION-HEADER-RECORD.
           05  RECORD-TYPE     PIC X(1).
           05  BATCH-NUMBER   PIC 9(5).
           05  BATCH-DATE     PIC X(8).

       FD  REPORT-FILE.
       01  REPORT-HEADER-RECORD.
           05  REPORT-TITLE    PIC X(50).
           05  REPORT-DATE     PIC X(8).
           05  PAGE-NUMBER     PIC 9(5).
       01  REPORT-DETAIL-RECORD.
           05  LINE-TYPE       PIC X(1).
           05  LINE-TEXT       PIC X(132).

       WORKING-STORAGE SECTION.
       01  WS-EOF-FLAG       PIC X VALUE 'N'.
           88  END-OF-FILE    VALUE 'Y'.
       01  WS-RECORD-COUNT   PIC 9(5) VALUE 0.
       01  WS-COUNT          PIC 9(5) VALUE 0.
       01  WS-INPUT          PIC X(10).
       01  WS-OUTPUT         PIC X(10).

       PROCEDURE DIVISION.
       1000-MAINLINE.
           PERFORM 2000-OPEN-FILES
           PERFORM 3000-PROCESS-FILES
           PERFORM 9000-CLOSE-FILES
           STOP RUN.

      ******************************************************************
      * File Operations Section
      ******************************************************************
       2000-OPEN-FILES.
           OPEN INPUT CUSTOMER-FILE
           OPEN I-O TRANSACTION-FILE
           OPEN OUTPUT REPORT-FILE.

       3000-PROCESS-FILES.
           PERFORM 3100-READ-CUSTOMER
           PERFORM 3200-UPDATE-TRANSACTIONS
           PERFORM 3300-DELETE-OLD-RECORDS
           PERFORM 3400-START-AT-KEY
           PERFORM 3500-WRITE-REPORTS
           PERFORM 3600-DATA-REFERENCES.

       3100-READ-CUSTOMER.
           READ CUSTOMER-FILE
               AT END MOVE 'Y' TO WS-EOF-FLAG
           END-READ.

       3200-UPDATE-TRANSACTIONS.
           REWRITE TRANSACTION-RECORD
               INVALID KEY DISPLAY "REWRITE FAILED"
           END-REWRITE.

       3300-DELETE-OLD-RECORDS.
           DELETE TRANSACTION-FILE
               INVALID KEY DISPLAY "DELETE FAILED"
           END-DELETE.

       3400-START-AT-KEY.
           START TRANSACTION-FILE
               KEY IS GREATER THAN TRNX-ID
               INVALID KEY DISPLAY "START FAILED"
           END-START.

       3500-WRITE-REPORTS.
           WRITE REPORT-RECORD FROM SPACES
               INVALID KEY DISPLAY "WRITE FAILED"
           END-WRITE
           ADD 1 TO WS-RECORD-COUNT.

       3600-DATA-REFERENCES.
           COMPUTE WS-RECORD-COUNT = WS-RECORD-COUNT + 1.
           SET END-OF-FILE TO TRUE.
           ADD WS-COUNT TO WS-RECORD-COUNT.
           SUBTRACT 1 FROM WS-RECORD-COUNT.
           MULTIPLY WS-RECORD-COUNT BY 2.
           DIVIDE WS-RECORD-COUNT BY 2.
           CALL 'SUBPROGRAM' USING WS-INPUT WS-OUTPUT.

       9000-CLOSE-FILES.
           CLOSE CUSTOMER-FILE
           CLOSE TRANSACTION-FILE
           CLOSE REPORT-FILE.
