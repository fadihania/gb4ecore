package com.fadihania;

final class RepositoryInspectionSummary {
    private int ecoreFiles;
    private int skippedSmall;
    private int skippedFewCommits;
    private int alreadyRecorded;
    private int newlyAccepted;
    private boolean fullyInspected = true;
    private String reason;

    void foundEcoreFile() {
        ecoreFiles++;
    }

    void recordSkippedSmall() {
        skippedSmall++;
    }

    void recordSkippedFewCommits() {
        skippedFewCommits++;
    }

    void recordAlreadyRecorded() {
        alreadyRecorded++;
    }

    void recordNewlyAccepted() {
        newlyAccepted++;
    }

    void markUnclassified() {
        fullyInspected = false;
    }

    void markUnclassified(String reason) {
        fullyInspected = false;
        this.reason = reason;
    }

    void markExcludedBecauseRepositoryHasTooFewCommits(int repositoryCommits, int minimumCommits) {
        reason = "repository has only " + repositoryCommits
                + " total commit(s); no file can have " + minimumCommits + " file commit(s)";
    }

    int ecoreFiles() {
        return ecoreFiles;
    }

    int skippedSmall() {
        return skippedSmall;
    }

    int skippedFewCommits() {
        return skippedFewCommits;
    }

    int alreadyRecorded() {
        return alreadyRecorded;
    }

    int newlyAccepted() {
        return newlyAccepted;
    }

    String reason() {
        return reason;
    }

    RepositoryOutcome outcome() {
        if (!fullyInspected) {
            return RepositoryOutcome.SKIPPED_UNCLASSIFIED;
        }
        if (newlyAccepted > 0) {
            return RepositoryOutcome.ACCEPTED;
        }
        if (alreadyRecorded > 0) {
            return RepositoryOutcome.ALREADY_RECORDED;
        }
        return RepositoryOutcome.EXCLUDED;
    }
}
