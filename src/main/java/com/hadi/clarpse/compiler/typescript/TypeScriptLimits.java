package com.hadi.clarpse.compiler.typescript;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.UnaryOperator;

/**
 * Reads the limits that bound a TypeScript analysis. Each is set by a system property, else an
 * environment variable, else its default; a value that is not a
 * non-negative number is reported and ignored. Zero turns a limit off.
 */
public final class TypeScriptLimits {

    public static final String REQUEST_TIMEOUT_PROP = "clarpse.typescript.requestTimeoutSeconds";
    public static final String REQUEST_TIMEOUT_ENV = "CLARPSE_TS_REQUEST_TIMEOUT_SECONDS";
    public static final double DEFAULT_REQUEST_TIMEOUT_SECONDS = 120;
    public static final String HEAVY_TIMEOUT_PROP = "clarpse.typescript.heavyRequestTimeoutSeconds";
    public static final String HEAVY_TIMEOUT_ENV = "CLARPSE_TS_HEAVY_REQUEST_TIMEOUT_SECONDS";
    public static final double DEFAULT_HEAVY_TIMEOUT_SECONDS = 600;
    public static final String COMPILE_BUDGET_PROP = "clarpse.typescript.compileBudgetSeconds";
    public static final String COMPILE_BUDGET_ENV = "CLARPSE_TS_COMPILE_BUDGET_SECONDS";
    public static final double DEFAULT_COMPILE_BUDGET_SECONDS = 420;
    public static final String MAX_RESTARTS_PROP = "clarpse.typescript.maxRestarts";
    public static final String MAX_RESTARTS_ENV = "CLARPSE_TS_MAX_RESTARTS";
    public static final int DEFAULT_MAX_RESTARTS = 3;

    private static final Logger LOGGER = LogManager.getLogger(TypeScriptLimits.class);

    private TypeScriptLimits() {
    }

    /**
     * A limit in seconds.
     *
     * @param prop         The system property.
     * @param env          The environment variable.
     * @param defaultValue The value where none is set or the one set is not valid.
     * @return The seconds; zero for no limit.
     */
    public static double seconds(final String prop, final String env, final double defaultValue) {
        return seconds(prop, env, defaultValue, System::getenv);
    }

    /**
     * A limit in seconds, with the environment given rather than the process's own.
     *
     * @param prop         The system property.
     * @param env          The environment variable.
     * @param defaultValue The value where none is set or the one set is not valid.
     * @param environment  Looks an environment variable up by name; null where it is not set.
     * @return The seconds; zero for no limit.
     */
    public static double seconds(final String prop, final String env, final double defaultValue,
                                 final UnaryOperator<String> environment) {
        final String raw = raw(prop, env, environment);
        if (raw == null) {
            return defaultValue;
        }
        try {
            final double value = Double.parseDouble(raw);
            if (value >= 0 && !Double.isNaN(value) && !Double.isInfinite(value)) {
                return value;
            }
        } catch (final NumberFormatException e) {
            // reported below
        }
        LOGGER.warn("Invalid value '{}' for {}: it must be a number of seconds, zero or more. "
                + "Using the default.", raw, prop);
        return defaultValue;
    }

    /**
     * A count.
     *
     * @param prop         The system property.
     * @param env          The environment variable.
     * @param defaultValue The value where none is set or the one set is not valid.
     * @return The count; zero or more.
     */
    public static int count(final String prop, final String env, final int defaultValue) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(seconds(prop, env, defaultValue)));
    }

    private static String raw(final String prop, final String env, final UnaryOperator<String> environment) {
        final String fromProperty = System.getProperty(prop);
        if (fromProperty != null && !fromProperty.trim().isEmpty()) {
            return fromProperty.trim();
        }
        final String fromEnv = environment.apply(env);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return null;
    }
}
