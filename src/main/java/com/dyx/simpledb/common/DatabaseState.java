package com.dyx.simpledb.common;

public class DatabaseState {

    public enum DbState {
        UNINITIALIZED,
        INITIALIZED,
        LOADED
    }

    private static DbState dbState = DbState.UNINITIALIZED;

    public static synchronized DbState getState() {
        return dbState;
    }

    public static synchronized void setState(DbState newState) {
        dbState = newState;
    }
}
