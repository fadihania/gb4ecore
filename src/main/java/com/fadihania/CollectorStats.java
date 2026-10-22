package com.fadihania;

import java.util.HashSet;
import java.util.Set;

final class CollectorStats {
    private int matchingModelFiles;
    private int newlyAcceptedModelFiles;
    private int alreadyRecordedModelFiles;
    private int repositoriesScanned;
    private int skippedRepositories;
    private int excludedRepositories;
    private int failed;
    private final Set<String> discoveredFiles = new HashSet<>();
    private final Set<String> sizeQualifiedFiles = new HashSet<>();
    private final Set<String> historyQualifiedFiles = new HashSet<>();
    private final Set<String> failedFiles = new HashSet<>();

    void recordRepositoryScanned() {
        repositoriesScanned++;
    }

    void recordSkippedRepository() {
        skippedRepositories++;
    }

    void recordExcludedRepository() {
        excludedRepositories++;
    }

    void recordNewlyAcceptedModelFile() {
        matchingModelFiles++;
        newlyAcceptedModelFiles++;
    }

    void recordNewlyAcceptedModelFile(String fileUrl) {
        recordHistoryQualifiedFile(fileUrl);
        recordNewlyAcceptedModelFile();
    }

    void recordAlreadyRecordedModelFile(String fileUrl) {
        recordHistoryQualifiedFile(fileUrl);
        matchingModelFiles++;
        alreadyRecordedModelFiles++;
    }

    void recordDiscoveredFile(String fileUrl) {
        discoveredFiles.add(fileKey(fileUrl));
    }

    void recordSizeQualifiedFile(String fileUrl) {
        String key = fileKey(fileUrl);
        discoveredFiles.add(key);
        sizeQualifiedFiles.add(key);
    }

    void recordHistoryQualifiedFile(String fileUrl) {
        String key = fileKey(fileUrl);
        discoveredFiles.add(key);
        sizeQualifiedFiles.add(key);
        historyQualifiedFiles.add(key);
    }

    void recordFileFailure(String fileUrl) {
        String key = fileKey(fileUrl);
        discoveredFiles.add(key);
        if (failedFiles.add(key)) {
            failed++;
        }
    }

    void recordFailure() {
        failed++;
    }

    void recordRepositoryFailure() {
        recordFailure();
        recordSkippedRepository();
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
                excludedRepositories,
                failed,
                discoveredFiles.size(),
                sizeQualifiedFiles.size(),
                historyQualifiedFiles.size(),
                skippedFiles());
    }

    String formatSummary() {
        return "matchingModelFiles=" + matchingModelFiles
                + ", newlyAcceptedModelFiles=" + newlyAcceptedModelFiles
                + ", alreadyRecordedModelFiles=" + alreadyRecordedModelFiles
                + ", repositoriesScanned=" + repositoriesScanned
                + ", skippedRepositories=" + skippedRepositories
                + ", excludedRepositories=" + excludedRepositories
                + ", discovered=" + discoveredFiles.size()
                + ", sizeQualified=" + sizeQualifiedFiles.size()
                + ", historyQualified=" + historyQualifiedFiles.size()
                + ", skipped=" + skippedFiles()
                + ", failed=" + failed;
    }

    String formatCompletedScan() {
        return snapshot().formatCompletedScan();
    }

    private int skippedFiles() {
        return Math.max(0, discoveredFiles.size() - historyQualifiedFiles.size() - failedFiles.size());
    }

    private String fileKey(String fileUrl) {
        return GitHubFileKey.fromBlobUrl(fileUrl)
                .map(GitHubFileKey::toString)
                .orElse(fileUrl == null ? "" : fileUrl);
    }

    record Snapshot(
            int matchingModelFiles,
            int newlyAcceptedModelFiles,
            int alreadyRecordedModelFiles,
            int repositoriesScanned,
            int skippedRepositories,
            int excludedRepositories,
            int failed,
            int discovered,
            int sizeQualified,
            int historyQualified,
            int skipped
    ) {
        Snapshot deltaFrom(Snapshot previous) {
            return new Snapshot(
                    matchingModelFiles - previous.matchingModelFiles,
                    newlyAcceptedModelFiles - previous.newlyAcceptedModelFiles,
                    alreadyRecordedModelFiles - previous.alreadyRecordedModelFiles,
                    repositoriesScanned - previous.repositoriesScanned,
                    skippedRepositories - previous.skippedRepositories,
                    excludedRepositories - previous.excludedRepositories,
                    failed - previous.failed,
                    discovered - previous.discovered,
                    sizeQualified - previous.sizeQualified,
                    historyQualified - previous.historyQualified,
                    skipped - previous.skipped);
        }

        String formatSummary() {
            return "matchingModelFiles=" + matchingModelFiles
                    + ", newlyAcceptedModelFiles=" + newlyAcceptedModelFiles
                    + ", alreadyRecordedModelFiles=" + alreadyRecordedModelFiles
                    + ", repositoriesScanned=" + repositoriesScanned
                    + ", skippedRepositories=" + skippedRepositories
                    + ", excludedRepositories=" + excludedRepositories
                    + ", discovered=" + discovered
                    + ", sizeQualified=" + sizeQualified
                    + ", historyQualified=" + historyQualified
                    + ", skipped=" + skipped
                    + ", failed=" + failed;
        }

        String formatCompletedScan() {
            return "Completed scan: " + formatScanCounts();
        }

        String formatScanCounts() {
            return "discovered=" + discovered
                    + ", size-qualified=" + sizeQualified
                    + ", history-qualified=" + historyQualified
                    + ", skipped=" + skipped
                    + ", skipped-repositories=" + skippedRepositories
                    + ", failed=" + failed;
        }
    }
}
