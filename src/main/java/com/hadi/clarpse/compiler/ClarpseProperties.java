package com.hadi.clarpse.compiler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads runtime properties bundled with Clarpse.
 */
public final class ClarpseProperties {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseProperties.class);
    private static final String RESOURCE_NAME = "clarpse.properties";
    private static final Properties PROPERTIES = loadProperties();

    private ClarpseProperties() {
    }

    public static int getInt(final String key, final int defaultValue) {
        final String rawValue = PROPERTIES.getProperty(key);
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (final NumberFormatException e) {
            LOGGER.warn("Invalid integer property {}={}, using default {}.", key, rawValue, defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(final String key, final long defaultValue) {
        final String rawValue = PROPERTIES.getProperty(key);
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (final NumberFormatException e) {
            LOGGER.warn("Invalid long property {}={}, using default {}.", key, rawValue, defaultValue);
            return defaultValue;
        }
    }

    private static Properties loadProperties() {
        final Properties properties = new Properties();
        try (InputStream inputStream = ClarpseProperties.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (inputStream == null) {
                LOGGER.warn("Missing {}, using built-in defaults.", RESOURCE_NAME);
                return properties;
            }
            properties.load(inputStream);
        } catch (final IOException e) {
            LOGGER.warn("Failed to load {}, using built-in defaults.", RESOURCE_NAME, e);
        }
        return properties;
    }
}
