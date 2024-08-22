package com.dyx.simpledb.common;

public class Error {
    // Common
    public static final Exception CacheFullException = new RuntimeException("Cache is full: Unable to store more data due to cache capacity limits.");
    public static final Exception FileExistsException = new RuntimeException("File already exists: Cannot create a new file with the same name.");
    public static final Exception FileNotExistsException = new RuntimeException("File not found: The specified file does not exist.");
    public static final Exception FileCannotRWException = new RuntimeException("File access error: The file cannot be read from or written to due to permission or other issues.");

    // DM
    public static final Exception BadLogFileException = new RuntimeException("Log file corruption detected: The log file is invalid or corrupted.");
    public static final Exception MemTooSmallException = new RuntimeException("Insufficient memory: The allocated memory is too small for the operation.");
    public static final Exception DataTooLargeException = new RuntimeException("Data size exceeds limit: The provided data is too large to be processed.");
    public static final Exception DatabaseBusyException = new RuntimeException("Database is currently busy: Operation cannot proceed as the database is locked or in use.");

    // TM
    public static final Exception BadXIDFileException = new RuntimeException("XID file corruption detected: The transaction ID file is invalid or corrupted.");

    // VM
    public static final Exception DeadlockException = new RuntimeException("Deadlock detected: Two or more transactions are waiting indefinitely for resources held by each other.");
    public static final Exception TimeoutException = new RuntimeException("Transaction timeout: Lock wait exceeded the maximum allowed time; consider retrying the operation.");
    public static final Exception ConcurrentUpdateException = new RuntimeException("Concurrent modification error: Data has been modified by another transaction.");
    public static final Exception NullEntryException = new RuntimeException("Null value error: Attempted operation on a null entry.");

    // TBM
    public static final Exception InvalidFieldException = new RuntimeException("Invalid field type: The field type provided does not match the expected type.");
    public static final Exception FieldNotFoundException = new RuntimeException("Field not found: The specified field does not exist in the table.");
    public static final Exception FieldNotIndexedException = new RuntimeException("Index missing: The field is not indexed, so the operation cannot proceed.");
    public static final Exception InvalidLogOpException = new RuntimeException("Invalid logical operation: The logical operation specified is not supported.");
    public static final Exception InvalidValuesException = new RuntimeException("Invalid input values: The provided values do not meet the expected criteria.");
    public static final Exception DuplicatedTableException = new RuntimeException("Table already exists: A table with the same name already exists in the database.");
    public static final Exception TableNotFoundException = new RuntimeException("Table not found: The specified table does not exist in the database.");
    public static final Exception TableNotCreateException = new RuntimeException("Table creation denied: The table name is restricted and cannot be used.");

    // Parser
    public static final Exception InvalidCommandException = new RuntimeException("Invalid command syntax: The command could not be parsed or is incorrect.");
    public static final Exception TableNoIndexException = new RuntimeException("Index missing: The table does not have an index for the requested operation.");

    // Transport
    public static final Exception InvalidPkgDataException = new RuntimeException("Package data error: The provided data format is invalid or corrupted.");

    // Server
    public static final Exception NestedTransactionException = new RuntimeException("Nested transaction not allowed: The system does not support transactions within transactions.");
    public static final Exception NoTransactionException = new RuntimeException("No active transaction: Attempted operation without an active transaction context.");

    // Launcher
    public static final Exception InvalidMemException = new RuntimeException("Memory allocation error: The specified memory configuration is invalid or inadequate.");
}
