package com.fadihania;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorStatsTest {
    @Test
    void acceptedRepositoryIncreasesMatchingAndNewlyAcceptedCounts() {
        CollectorStats stats = new CollectorStats();
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.recordNewlyAccepted();
        summary.recordNewlyAccepted();
        stats.recordRepositoryScanned();
        stats.recordRepositoryOutcome(summary);

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(2, snapshot.matchingModelFiles());
        assertEquals(2, snapshot.newlyAcceptedModelFiles());
        assertEquals(0, snapshot.alreadyRecordedModelFiles());
        assertEquals(1, snapshot.repositoriesScanned());
    }

    @Test
    void alreadyRecordedRepositoryIncreasesMatchingAndAlreadyRecordedCounts() {
        CollectorStats stats = new CollectorStats();
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.recordAlreadyRecorded();
        summary.recordAlreadyRecorded();
        summary.recordAlreadyRecorded();
        stats.recordRepositoryOutcome(summary);

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(3, snapshot.matchingModelFiles());
        assertEquals(0, snapshot.newlyAcceptedModelFiles());
        assertEquals(3, snapshot.alreadyRecordedModelFiles());
    }

    @Test
    void excludedRepositoryIncreasesScannedAndExcludedCounts() {
        CollectorStats stats = new CollectorStats();

        stats.recordRepositoryScanned();
        stats.recordExcludedRepository();

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(1, snapshot.repositoriesScanned());
        assertEquals(1, snapshot.excludedRepositories());
        assertEquals(0, snapshot.skippedRepositories());
    }

    @Test
    void unclassifiedAndSkippedRepositoriesIncreaseSkippedCount() {
        CollectorStats stats = new CollectorStats();
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        stats.recordSkippedRepository();
        summary.markUnclassified("tree failed");
        stats.recordRepositoryOutcome(summary);

        assertEquals(2, stats.snapshot().skippedRepositories());
    }

    @Test
    void snapshotDeltaShowsOnlyChangesSincePreviousSnapshot() {
        CollectorStats stats = new CollectorStats();
        CollectorStats.Snapshot before = stats.snapshot();

        stats.recordRepositoryScanned();
        stats.recordSkippedRepository();

        CollectorStats.Snapshot delta = stats.snapshot().deltaFrom(before);
        assertEquals(1, delta.repositoriesScanned());
        assertEquals(1, delta.skippedRepositories());
        assertTrue(delta.formatSummary().contains("repositoriesScanned=1"));
    }
}
