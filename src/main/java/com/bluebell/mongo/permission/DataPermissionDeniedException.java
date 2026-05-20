package com.bluebell.mongo.permission;

public class DataPermissionDeniedException extends RuntimeException {

    public DataPermissionDeniedException(String message) {
        super(message);
    }
}
