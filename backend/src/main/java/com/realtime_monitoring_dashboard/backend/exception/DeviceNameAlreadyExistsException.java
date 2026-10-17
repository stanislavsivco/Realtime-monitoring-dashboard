package com.realtime_monitoring_dashboard.backend.exception;

public class DeviceNameAlreadyExistsException extends RuntimeException {

    public DeviceNameAlreadyExistsException(String message) {
        super(message);
    }
}
