package com.dyx.simpledb.backend.utils;

public class Panic {
    public static void panic(Exception err) {
        err.printStackTrace();
        // System.exit(1);
    }
}
