package com.barberstudio.common.error;

/** Regla de negocio incumplida (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
