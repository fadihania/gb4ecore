package com.fadihania;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {
    @Test
    void parsesDefaults() {
        AppConfig config = AppConfig.parse(new String[0]);

        assertEquals(Path.of("out"), config.outDirectory());
        assertEquals(10_240, config.minSizeBytes());
        assertEquals(10, config.minCommits());
        assertEquals(10_240, config.sizeStepBytes());
        assertFalse(config.hasLimit());
    }

    @Test
    void parsesOptions() {
        AppConfig config = AppConfig.parse(new String[]{
                "--out", "dataset",
                "--min-size", "20000",
                "--min-commits", "12",
                "--size-step", "5000",
                "--limit", "3"
        });

        assertEquals(Path.of("dataset"), config.outDirectory());
        assertEquals(20_000, config.minSizeBytes());
        assertEquals(12, config.minCommits());
        assertEquals(5_000, config.sizeStepBytes());
        assertEquals(3, config.limit());
        assertTrue(config.hasLimit());
    }

    @Test
    void rejectsUnknownOption() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.parse(new String[]{"--bogus"}));
    }
}
