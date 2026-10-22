package com.fadihania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsLogTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAndOverwritesStatsLog() throws Exception {
        Path file = tempDir.resolve("stats.log");
        Files.writeString(file, "old content", StandardCharsets.UTF_8);

        try (StatsLog log = StatsLog.open(tempDir)) {
            log.runStarted(AppConfig.parse(new String[0]));
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(content.contains("old content"));
        assertTrue(content.contains("Run started"));
        assertTrue(content.contains("minSizeBytes=10240"));
    }

    @Test
    void writesBucketDetailsAndFinalStats() throws Exception {
        CollectorStats stats = new CollectorStats();
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();
        summary.recordNewlyAccepted();
        summary.recordAlreadyRecorded();
        stats.recordRepositoryScanned();
        stats.recordRepositoryOutcome(summary);

        try (StatsLog log = StatsLog.open(tempDir)) {
            log.bucketStarted("extension:ecore size:10241..20480", 10241, 20480);
            log.bucketSearchReported(1000, true);
            log.rangeSplit(
                    new SearchSizeRange(10241, 20480),
                    new SearchSizeRange(10241, 15360),
                    new SearchSizeRange(15361, 20480),
                    1000);
            log.rangeCannotSplit(new SearchSizeRange(10241, 10241), 1000);
            log.bucketFinished("extension:ecore size:10241..20480", stats.snapshot());
            log.runFinished(stats, false);
        }

        String content = Files.readString(tempDir.resolve("stats.log"), StandardCharsets.UTF_8);
        assertTrue(content.contains("Bucket started: sizeRange=10241..20480"));
        assertTrue(content.contains("Bucket reported results=1000, incomplete=true"));
        assertTrue(content.contains("GitHub search may cap this bucket at 1,000 results"));
        assertTrue(content.contains("GitHub marked this bucket as incomplete"));
        assertTrue(content.contains("Bucket split: parent=10241..20480"));
        assertTrue(content.contains("children=10241..15360 and 15361..20480"));
        assertTrue(content.contains("cannot be split further by size"));
        assertTrue(content.contains("--------------------------------------------------------------------------------"));
        assertTrue(content.contains("Final stats: matchingModelFiles=2"));
        assertTrue(content.contains("Completed scan: discovered=0, size-qualified=0, history-qualified=0, skipped=0, skipped-repositories=0, failed=0"));
        assertTrue(content.contains("stoppedByLimit=false"));
    }
}
