package com.redgreenrefactor.cli;

/**
 * Exception thrown when there is a configuration error.
 */
public class ConfigurationException extends Exception {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
