package com.dyx.simpledb.util;

public class ResponseV {

    private int statusCode;
    private String message;

    public ResponseV(String message) {
        this.statusCode = 200;
        this.message = message;
    }

    public ResponseV(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}