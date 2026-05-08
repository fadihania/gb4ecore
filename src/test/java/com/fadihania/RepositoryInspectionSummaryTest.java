package com.fadihania;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepositoryInspectionSummaryTest {
    @Test
    void fullyInspectedRepositoryWithoutMatchesIsExcluded() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.foundEcoreFile();
        summary.recordSkippedSmall();
        summary.foundEcoreFile();
        summary.recordSkippedFewCommits();

        assertEquals(RepositoryOutcome.EXCLUDED, summary.outcome());
    }

    @Test
    void alreadyRecordedRepositoryIsNotExcluded() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.foundEcoreFile();
        summary.recordAlreadyRecorded();

        assertEquals(RepositoryOutcome.ALREADY_RECORDED, summary.outcome());
    }

    @Test
    void unclassifiedRepositoryIsSkipped() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.markUnclassified();

        assertEquals(RepositoryOutcome.SKIPPED_UNCLASSIFIED, summary.outcome());
    }

    @Test
    void unclassifiedRepositoryCanStoreReason() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.markUnclassified("tree load failed");

        assertEquals(RepositoryOutcome.SKIPPED_UNCLASSIFIED, summary.outcome());
        assertEquals("tree load failed", summary.reason());
    }

    @Test
    void commitCountFailureReasonMakesRepositoryUnclassified() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.markUnclassified(GitHubEcoreCollector.commitCountFailureReason(
                "models/example.ecore",
                new RuntimeException("GitHub API failed")));

        assertEquals(RepositoryOutcome.SKIPPED_UNCLASSIFIED, summary.outcome());
        assertEquals(
                "failed to count commits for models/example.ecore: GitHub API failed",
                summary.reason());
    }

    @Test
    void acceptedRepositoryWinsOverOtherCounters() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.foundEcoreFile();
        summary.recordSkippedFewCommits();
        summary.foundEcoreFile();
        summary.recordNewlyAccepted();

        assertEquals(RepositoryOutcome.ACCEPTED, summary.outcome());
    }

    @Test
    void explainsRepositoryCommitPrecheckExclusion() {
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();

        summary.markExcludedBecauseRepositoryHasTooFewCommits(5, 10);

        assertEquals(RepositoryOutcome.EXCLUDED, summary.outcome());
        assertEquals("repository has only 5 total commit(s); no file can have 10 file commit(s)", summary.reason());
    }
}
