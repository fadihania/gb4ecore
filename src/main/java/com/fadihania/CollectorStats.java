package com.fadihania;

final class CollectorStats {
    private int matchingModelFiles;
    private int newlyAcceptedModelFiles;
    private int alreadyRecordedModelFiles;
    private int repositoriesScanned;
    private int skippedRepositories;
    private int excludedRepositories;

    void recordRepositoryScanned() {
        repositoriesScanned++;
    }

    void recordSkippedRepository() {
        skippedRepositories++;
    }

    void recordExcludedRepository() {
        excludedRepositories++;
    }

    void recordRepositoryOutcome(RepositoryInspectionSummary summary) {
        matchingModelFiles += summary.newlyAccepted() + summary.alreadyRecorded();
        newlyAcceptedModelFiles += summary.newlyAccepted();
        alreadyRecordedModelFiles += summary.alreadyRecorded();
        if (summary.outcome() == RepositoryOutcome.SKIPPED_UNCLASSIFIED) {
            recordSkippedRepository();
        }
    }

    Snapshot snapshot() {
        return new Snapshot(
                matchingModelFiles,
                newlyAcceptedModelFiles,
                alreadyRecordedModelFiles,
                repositoriesScanned,
                skippedRepositories,
                excludedRepositories);
    }

    String formatSummary() {
        return "matchingModelFiles=" + matchingModelFiles
                + ", newlyAcceptedModelFiles=" + newlyAcceptedModelFiles
                + ", alreadyRecordedModelFiles=" + alreadyRecordedModelFiles
                + ", repositoriesScanned=" + repositoriesScanned
                + ", skippedRepositories=" + skippedRepositories
                + ", excludedRepositories=" + excludedRepositories;
    }

    record Snapshot(
            int matchingModelFiles,
            int newlyAcceptedModelFiles,
            int alreadyRecordedModelFiles,
            int repositoriesScanned,
            int skippedRepositories,
            int excludedRepositories
    ) {
        Snapshot deltaFrom(Snapshot previous) {
            return new Snapshot(
                    matchingModelFiles - previous.matchingModelFiles,
                    newlyAcceptedModelFiles - previous.newlyAcceptedModelFiles,
                    alreadyRecordedModelFiles - previous.alreadyRecordedModelFiles,
                    repositoriesScanned - previous.repositoriesScanned,
                    skippedRepositories - previous.skippedRepositories,
                    excludedRepositories - previous.excludedRepositories);
        }

        String formatSummary() {
            return "matchingModelFiles=" + matchingModelFiles
                    + ", newlyAcceptedModelFiles=" + newlyAcceptedModelFiles
                    + ", alreadyRecordedModelFiles=" + alreadyRecordedModelFiles
                    + ", repositoriesScanned=" + repositoriesScanned
                    + ", skippedRepositories=" + skippedRepositories
                    + ", excludedRepositories=" + excludedRepositories;
        }
    }
}
