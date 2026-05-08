package com.fadihania;

import java.nio.file.Path;
import java.util.Objects;

record AppConfig(Path outDirectory, long minSizeBytes, int minCommits, long sizeStepBytes, int limit) {
    static final long DEFAULT_MIN_SIZE_BYTES = 10_240;
    static final int DEFAULT_MIN_COMMITS = 10;
    static final long DEFAULT_SIZE_STEP_BYTES = 10_240;
    static final long DEFAULT_MAX_SEARCHABLE_SIZE_BYTES = 393_216;
    static final int NO_LIMIT = -1;

    AppConfig {
        Objects.requireNonNull(outDirectory, "outDirectory");
        if (minSizeBytes < 1) {
            throw new IllegalArgumentException("--min-size must be greater than zero.");
        }
        if (minCommits < 1) {
            throw new IllegalArgumentException("--min-commits must be greater than zero.");
        }
        if (sizeStepBytes < 1) {
            throw new IllegalArgumentException("--size-step must be greater than zero.");
        }
        if (limit == 0 || limit < NO_LIMIT) {
            throw new IllegalArgumentException("--limit must be positive when provided.");
        }
    }

    static AppConfig parse(String[] args) {
        Path out = Path.of("out");
        long minSize = DEFAULT_MIN_SIZE_BYTES;
        int minCommits = DEFAULT_MIN_COMMITS;
        long sizeStep = DEFAULT_SIZE_STEP_BYTES;
        int limit = NO_LIMIT;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--out" -> out = Path.of(requiredValue(args, ++i, arg));
                case "--min-size" -> minSize = parseLong(requiredValue(args, ++i, arg), arg);
                case "--min-commits" -> minCommits = parseInt(requiredValue(args, ++i, arg), arg);
                case "--size-step" -> sizeStep = parseLong(requiredValue(args, ++i, arg), arg);
                case "--limit" -> limit = parseInt(requiredValue(args, ++i, arg), arg);
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        return new AppConfig(out, minSize, minCommits, sizeStep, limit);
    }

    boolean hasLimit() {
        return limit != NO_LIMIT;
    }

    private static String requiredValue(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value.");
        }
        return args[index];
    }

    private static int parseInt(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be an integer.");
        }
    }

    private static long parseLong(String value, String option) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be an integer.");
        }
    }
}
