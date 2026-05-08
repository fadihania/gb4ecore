package com.fadihania;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedSearchIterable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

final class GitHubEcoreCollector {
    private final AppConfig config;
    private final GitHub github;
    private final CommandLineLogger logger;

    private GitHubEcoreCollector(AppConfig config, GitHub github, CommandLineLogger logger) {
        this.config = config;
        this.github = github;
        this.logger = logger;
    }

    static GitHubEcoreCollector connect(AppConfig config, String token, CommandLineLogger logger) throws IOException {
        GitHub github = GitHub.connectUsingOAuth(token);
        if (!github.isCredentialValid()) {
            throw new IOException("GITHUB_TOKEN was rejected by GitHub.");
        }
        return new GitHubEcoreCollector(config, github, logger);
    }

    void run() throws IOException {
        Files.createDirectories(config.outDirectory());

        CsvCatalog catalog = CsvCatalog.open(config.outDirectory());
        ExcludedRepositories excludedRepositories = ExcludedRepositories.open(config.outDirectory());
        UnclassifiedRepositories unclassifiedRepositories = UnclassifiedRepositories.open(config.outDirectory());
        CollectorStats stats = new CollectorStats();
        Set<String> inspectedRepositories = new HashSet<>();

        SearchRunState runState = new SearchRunState();
        try (StatsLog statsLog = StatsLog.open(config.outDirectory())) {
            statsLog.runStarted(config);
            for (long start = config.minSizeBytes();
                 start <= AppConfig.DEFAULT_MAX_SEARCHABLE_SIZE_BYTES && !limitReached(runState.accepted);
                 start += config.sizeStepBytes()) {
                long end = Math.min(start + config.sizeStepBytes() - 1, AppConfig.DEFAULT_MAX_SEARCHABLE_SIZE_BYTES);
                processSearchRange(
                        new SearchSizeRange(start, end),
                        catalog,
                        excludedRepositories,
                        unclassifiedRepositories,
                        inspectedRepositories,
                        stats,
                        statsLog,
                        runState);
            }

            if (runState.stoppedByLimit) {
                logger.warn("Run stopped after reaching --limit; the repository being inspected at that moment was not excluded unless it had already been fully checked.");
            }
            statsLog.runFinished(stats, runState.stoppedByLimit);
        }
        logger.info("Collector finished. Accepted " + runState.accepted + " model(s).");
        logger.info("Final stats: " + stats.formatSummary());
    }

    private void processSearchRange(
            SearchSizeRange range,
            CsvCatalog catalog,
            ExcludedRepositories excludedRepositories,
            UnclassifiedRepositories unclassifiedRepositories,
            Set<String> inspectedRepositories,
            CollectorStats stats,
            StatsLog statsLog,
            SearchRunState runState
    ) throws IOException {
        if (limitReached(runState.accepted)) {
            runState.stoppedByLimit = true;
            return;
        }

        String query = range.query();
        CollectorStats.Snapshot bucketStartStats = stats.snapshot();
        logger.info("Searching GitHub code bucket: " + query);
        statsLog.bucketStarted(query, range.start(), range.end());

        PagedSearchIterable<GHContent> results = github.searchContent().q(query).list().withPageSize(100);
        int totalCount = results.getTotalCount();
        boolean incomplete = results.isIncomplete();
        logger.info("Bucket reported " + totalCount + " result(s).");
        statsLog.bucketSearchReported(totalCount, incomplete);
        if (totalCount >= 1000) {
            logger.warn("GitHub search may cap this bucket at 1,000 results.");
        }
        if (incomplete) {
            logger.warn("GitHub marked this bucket as incomplete.");
        }

        SearchRangeAction action = SearchSizeRange.actionFor(totalCount, range);
        if (action == SearchRangeAction.SPLIT) {
            SearchSizeRange[] children = range.split();
            logger.warn("Splitting capped size range " + range
                    + " into " + children[0] + " and " + children[1] + ".");
            statsLog.rangeSplit(range, children[0], children[1], totalCount);
            CollectorStats.Snapshot bucketStats = stats.snapshot().deltaFrom(bucketStartStats);
            logger.info("Finished bucket: " + query + " | " + bucketStats.formatSummary());
            statsLog.bucketFinished(query, bucketStats);
            processSearchRange(
                    children[0],
                    catalog,
                    excludedRepositories,
                    unclassifiedRepositories,
                    inspectedRepositories,
                    stats,
                    statsLog,
                    runState);
            if (!runState.stoppedByLimit) {
                processSearchRange(
                        children[1],
                        catalog,
                        excludedRepositories,
                        unclassifiedRepositories,
                        inspectedRepositories,
                        stats,
                        statsLog,
                        runState);
            }
            return;
        }

        if (action == SearchRangeAction.PROCESS_CAPPED) {
            logger.warn("Size range " + range + " still reports " + totalCount
                    + " result(s), but it cannot be split further by size.");
            statsLog.rangeCannotSplit(range, totalCount);
        }

        for (GHContent content : results) {
            if (limitReached(runState.accepted)) {
                runState.stoppedByLimit = true;
                break;
            }

            String repositoryName = content.getOwner().getFullName();
            if (excludedRepositories.contains(repositoryName)) {
                stats.recordSkippedRepository();
                logger.info("Skipping excluded repository: " + repositoryName);
                continue;
            }
            if (!inspectedRepositories.add(repositoryName)) {
                stats.recordSkippedRepository();
                logger.info("Skipping already inspected repository in this run: " + repositoryName);
                continue;
            }

            GHRepository repository = resolveRepository(repositoryName);
            if (repository == null) {
                stats.recordSkippedRepository();
                continue;
            }
            RepositoryInspectionResult result;
            try {
                result = inspectRepository(
                        repository,
                        catalog,
                        excludedRepositories,
                        unclassifiedRepositories,
                        runState.accepted,
                        stats);
            } catch (IOException e) {
                stats.recordSkippedRepository();
                logger.warn("Skipping repository " + repositoryName
                        + " after inspection failed: " + e.getMessage());
                unclassifiedRepositories.add(repositoryName, e.getMessage());
                logger.info("Added repository to unclassified list: " + repositoryName);
                continue;
            }
            runState.accepted += result.acceptedModels();
            if (result.stoppedByLimit()) {
                runState.stoppedByLimit = true;
                break;
            }
        }

        CollectorStats.Snapshot bucketStats = stats.snapshot().deltaFrom(bucketStartStats);
        logger.info("Finished bucket: " + query + " | " + bucketStats.formatSummary());
        statsLog.bucketFinished(query, bucketStats);
    }

    private RepositoryInspectionResult inspectRepository(
            GHRepository repository,
            CsvCatalog catalog,
            ExcludedRepositories excludedRepositories,
            UnclassifiedRepositories unclassifiedRepositories,
            int acceptedBeforeRepository,
            CollectorStats stats
    ) throws IOException {
        String repositoryName = repository.getFullName();
        logger.repository(repositoryName);
        stats.recordRepositoryScanned();
        RepositoryInspectionSummary summary = new RepositoryInspectionSummary();
        String defaultBranch = repository.getDefaultBranch();
        if (defaultBranch == null || defaultBranch.isBlank()) {
            logger.warn("Skipping repository because GitHub did not provide a default branch.");
            summary.markUnclassified("GitHub did not provide a default branch");
            recordUnclassifiedRepository(unclassifiedRepositories, repositoryName, summary);
            logRepositoryOutcome(summary);
            stats.recordRepositoryOutcome(summary);
            return new RepositoryInspectionResult(0, false);
        }

        RepositoryCommitPrecheck precheck = precheckRepositoryCommits(repository, config.minCommits());
        if (!precheck.canHaveMatchingFile()) {
            summary.markExcludedBecauseRepositoryHasTooFewCommits(precheck.commitsChecked(), config.minCommits());
            excludedRepositories.add(repositoryName);
            stats.recordExcludedRepository();
            logger.info("Added repository to excluded list: " + summary.reason() + ".");
            logRepositoryOutcome(summary);
            stats.recordRepositoryOutcome(summary);
            return new RepositoryInspectionResult(0, false);
        }

        logger.info("Inspecting Ecore models in default branch: " + defaultBranch);

        GHTree tree = loadRepositoryTree(repository, defaultBranch, summary);
        if (tree == null) {
            recordUnclassifiedRepository(unclassifiedRepositories, repositoryName, summary);
            logRepositoryOutcome(summary);
            stats.recordRepositoryOutcome(summary);
            return new RepositoryInspectionResult(0, false);
        }
        int accepted = 0;

        if (tree.isTruncated()) {
            summary.markUnclassified("GitHub returned a truncated repository tree");
            logger.warn("Repository tree is truncated by GitHub; this repository will only be logged and will not be added to the excluded list.");
        }

        for (GHTreeEntry entry : tree.getTree()) {
            if (!"blob".equals(entry.getType()) || !entry.getPath().toLowerCase().endsWith(".ecore")) {
                continue;
            }

            summary.foundEcoreFile();
            String fileUrl = fileHtmlUrl(repository, defaultBranch, entry.getPath());
            if (catalog.containsFileUrl(fileUrl)) {
                summary.recordAlreadyRecorded();
                logger.info("Skipping already recorded matching file: " + fileUrl);
                continue;
            }
            if (entry.getSize() < config.minSizeBytes()) {
                summary.recordSkippedSmall();
                logger.info("Skipping small file: " + entry.getPath() + " (" + entry.getSize() + " bytes)");
                continue;
            }
            if (limitReached(acceptedBeforeRepository + accepted)) {
                summary.markUnclassified("run stopped after reaching --limit before repository inspection completed");
                recordUnclassifiedRepository(unclassifiedRepositories, repositoryName, summary);
                logRepositoryOutcome(summary);
                stats.recordRepositoryOutcome(summary);
                return new RepositoryInspectionResult(accepted, true);
            }

            logger.info("Inspecting candidate " + entry.getPath());
            int totalCommits;
            try {
                totalCommits = countCommits(repository, entry.getPath());
            } catch (IOException | RuntimeException e) {
                summary.markUnclassified(commitCountFailureReason(entry.getPath(), e));
                logger.warn("Failed to count commits for " + entry.getPath()
                        + "; repository will be marked unclassified: " + e.getMessage());
                recordUnclassifiedRepository(unclassifiedRepositories, repositoryName, summary);
                logRepositoryOutcome(summary);
                stats.recordRepositoryOutcome(summary);
                return new RepositoryInspectionResult(accepted, false);
            }
            if (totalCommits < config.minCommits()) {
                summary.recordSkippedFewCommits();
                logger.info("Skipping " + entry.getPath() + ": " + totalCommits
                        + " file commit(s), minimum required is " + config.minCommits() + ".");
                continue;
            }

            GHContent content = repository.getFileContent(entry.getPath());
            ModelRecord record = new ModelRecord(
                    content.getName(),
                    repositoryName,
                    repository.getHtmlUrl().toString(),
                    content.getHtmlUrl(),
                    content.getSize(),
                    totalCommits);
            catalog.append(record);
            summary.recordNewlyAccepted();
            accepted++;
            logger.info("Recorded matching model " + entry.getPath() + " from " + repositoryName
                    + " (" + totalCommits + " total file commits).");
        }

        if (summary.ecoreFiles() == 0) {
            logger.info("No Ecore files found in repository tree.");
        }
        if (summary.outcome() == RepositoryOutcome.EXCLUDED) {
            excludedRepositories.add(repositoryName);
            stats.recordExcludedRepository();
            logger.info("Added repository to excluded list: no Ecore model matched size and commit criteria.");
        } else if (summary.outcome() == RepositoryOutcome.ALREADY_RECORDED) {
            logger.info("Repository has matching Ecore model(s) already recorded in the CSV; it will not be excluded.");
        } else if (summary.outcome() == RepositoryOutcome.SKIPPED_UNCLASSIFIED) {
            logger.warn("Repository was not fully classified; it will only be logged and will not be excluded.");
            recordUnclassifiedRepository(unclassifiedRepositories, repositoryName, summary);
        }
        logRepositoryOutcome(summary);
        stats.recordRepositoryOutcome(summary);
        return new RepositoryInspectionResult(accepted, false);
    }

    private void recordUnclassifiedRepository(
            UnclassifiedRepositories unclassifiedRepositories,
            String repositoryName,
            RepositoryInspectionSummary summary
    ) throws IOException {
        unclassifiedRepositories.add(repositoryName, summary.reason());
        logger.info("Added repository to unclassified list: " + repositoryName);
    }

    private void logRepositoryOutcome(RepositoryInspectionSummary summary) {
        logger.info("Repository outcome: " + summary.outcome()
                + " | ecoreFiles=" + summary.ecoreFiles()
                + ", skippedSmall=" + summary.skippedSmall()
                + ", skippedFewCommits=" + summary.skippedFewCommits()
                + ", alreadyRecorded=" + summary.alreadyRecorded()
                + ", newlyAccepted=" + summary.newlyAccepted()
                + (summary.reason() == null ? "" : ", reason=\"" + summary.reason() + "\""));
    }

    private GHTree loadRepositoryTree(
            GHRepository repository,
            String defaultBranch,
            RepositoryInspectionSummary summary
    ) {
        try {
            logger.info("Loading recursive repository tree for branch: " + defaultBranch);
            return repository.getTreeRecursive(defaultBranch, 1);
        } catch (IOException e) {
            summary.markUnclassified("failed to load recursive tree for branch "
                    + defaultBranch + ": " + e.getMessage());
            logger.warn("Failed to load recursive tree for " + repository.getFullName()
                    + " on branch " + defaultBranch + ": " + e.getMessage());
            logger.warn("Repository was not fully classified; it will only be logged and will not be excluded.");
            return null;
        }
    }

    static String commitCountFailureReason(String path, Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return "failed to count commits for " + path + ": " + message;
    }

    private RepositoryCommitPrecheck precheckRepositoryCommits(GHRepository repository, int minimumCommits) throws IOException {
        int commits = 0;
        for (var ignored : repository.listCommits().withPageSize(100)) {
            commits++;
            if (commits >= minimumCommits) {
                break;
            }
        }
        return RepositoryCommitPrecheck.fromCommitsChecked(commits, minimumCommits);
    }

    private int countCommits(GHRepository repository, String path) throws IOException {
        int commits = 0;
        for (var ignored : repository.queryCommits().path(path).pageSize(100).list()) {
            commits++;
        }
        return commits;
    }

    private boolean limitReached(int accepted) {
        return config.hasLimit() && accepted >= config.limit();
    }

    private GHRepository resolveRepository(String repositoryName) throws IOException {
        try {
            GHRepository repository = github.getRepository(repositoryName);
            if (repository.getDefaultBranch() == null || repository.getDefaultBranch().isBlank()) {
                logger.warn("Repository " + repositoryName + " has no default branch in GitHub metadata.");
            }
            return repository;
        } catch (IOException e) {
            logger.warn("Skipping repository " + repositoryName + " because it could not be loaded: " + e.getMessage());
            return null;
        }
    }

    private String fileHtmlUrl(GHRepository repository, String defaultBranch, String path) {
        return repository.getHtmlUrl() + "/blob/" + defaultBranch + "/" + path;
    }

    private static final class SearchRunState {
        private int accepted;
        private boolean stoppedByLimit;
    }

    private record RepositoryInspectionResult(int acceptedModels, boolean stoppedByLimit) {
    }
}
