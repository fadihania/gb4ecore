package com.fadihania;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class CommandLineLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    void info(String message) {
        System.out.println(prefix("INFO") + message);
    }

    void warn(String message) {
        System.out.println(prefix("WARN") + message);
    }

    void error(String message) {
        System.err.println(prefix("ERROR") + message);
    }

    void repository(String repositoryName) {
        System.out.println();
        System.out.println("================================================================================");
        info("Repository: " + repositoryName);
        System.out.println("================================================================================");
    }

    private String prefix(String level) {
        return "[" + FORMATTER.format(LocalDateTime.now()) + "] [" + level + "] ";
    }
}
