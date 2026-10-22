package com.barberstudio.common.error;

import java.util.Map;

/** Conflicto con el estado actual (HTTP 409): duplicados, horarios ocupados. */
public class ConflictException extends RuntimeException {
    private final Map<String, Object> properties;
    public ConflictException(String message) { this(message, Map.of()); }
    public ConflictException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = properties;
    }
    public Map<String, Object> getProperties() { return properties; }
}
