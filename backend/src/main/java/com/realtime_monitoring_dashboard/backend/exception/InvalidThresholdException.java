package com.realtime_monitoring_dashboard.backend.exception;

public class InvalidThresholdException extends RuntimeException {

    public InvalidThresholdException(String message) {
        super(message);
    }
}