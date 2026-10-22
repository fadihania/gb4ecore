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
    void directSearchResultAcceptanceIncreasesMatchingAndNewlyAcceptedCounts() {
        CollectorStats stats = new CollectorStats();

        stats.recordNewlyAcceptedModelFile("https://github.com/owner/repo/blob/main/model.ecore");

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(1, snapshot.matchingModelFiles());
        assertEquals(1, snapshot.newlyAcceptedModelFiles());
        assertEquals(0, snapshot.alreadyRecordedModelFiles());
        assertEquals(1, snapshot.discovered());
        assertEquals(1, snapshot.sizeQualified());
        assertEquals(1, snapshot.historyQualified());
    }

    @Test
    void uniqueFilePipelineCountersAreNotDoubleCountedByRefVariant() {
        CollectorStats stats = new CollectorStats();
        String branchUrl = "https://github.com/owner/repo/blob/main/model/example.ecore";
        String commitUrl = "https://github.com/owner/repo/blob/d67582e93f7d3fefed1158a7d32580c2e3d745f2/model/example.ecore";

        stats.recordDiscoveredFile(branchUrl);
        stats.recordDiscoveredFile(commitUrl);
        stats.recordSizeQualifiedFile(branchUrl);
        stats.recordSizeQualifiedFile(commitUrl);
        stats.recordHistoryQualifiedFile(commitUrl);

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(1, snapshot.discovered());
        assertEquals(1, snapshot.sizeQualified());
        assertEquals(1, snapshot.historyQualified());
        assertEquals(0, snapshot.skipped());
    }

    @Test
    void skippedFilesAreDiscoveredFilesThatDoNotHistoryQualify() {
        CollectorStats stats = new CollectorStats();

        stats.recordDiscoveredFile("https://github.com/owner/repo/blob/main/small.ecore");
        stats.recordSizeQualifiedFile("https://github.com/owner/repo/blob/main/low-history.ecore");
        stats.recordHistoryQualifiedFile("https://github.com/owner/repo/blob/main/match.ecore");

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(3, snapshot.discovered());
        assertEquals(2, snapshot.sizeQualified());
        assertEquals(1, snapshot.historyQualified());
        assertEquals(2, snapshot.skipped());
    }

    @Test
    void alreadyRecordedFileCountsAsHistoryQualifiedWhenEncountered() {
        CollectorStats stats = new CollectorStats();

        stats.recordAlreadyRecordedModelFile("https://github.com/owner/repo/blob/main/model.ecore");

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(1, snapshot.matchingModelFiles());
        assertEquals(1, snapshot.alreadyRecordedModelFiles());
        assertEquals(1, snapshot.discovered());
        assertEquals(1, snapshot.sizeQualified());
        assertEquals(1, snapshot.historyQualified());
    }

    @Test
    void failuresIncrementFailedAndRepositorySkipCounts() {
        CollectorStats stats = new CollectorStats();

        stats.recordFileFailure("https://github.com/owner/repo/blob/main/model.ecore");
        stats.recordRepositoryFailure();

        CollectorStats.Snapshot snapshot = stats.snapshot();
        assertEquals(2, snapshot.failed());
        assertEquals(1, snapshot.skippedRepositories());
        assertEquals(1, snapshot.discovered());
        assertEquals(0, snapshot.skipped());
    }

    @Test
    void formatsCompactCompletedScanSummary() {
        CollectorStats stats = new CollectorStats();

        stats.recordDiscoveredFile("https://github.com/owner/repo/blob/main/small.ecore");
        stats.recordHistoryQualifiedFile("https://github.com/owner/repo/blob/main/match.ecore");
        stats.recordRepositoryFailure();

        assertEquals(
                "Completed scan: discovered=2, size-qualified=1, history-qualified=1, skipped=1, skipped-repositories=1, failed=1",
                stats.formatCompletedScan());
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
