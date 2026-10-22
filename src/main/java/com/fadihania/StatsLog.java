package com.fadihania;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class StatsLog implements Closeable {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final BufferedWriter writer;

    private StatsLog(BufferedWriter writer) {
        this.writer = writer;
    }

    static StatsLog open(Path outDirectory) throws IOException {
        Files.createDirectories(outDirectory);
        BufferedWriter writer = Files.newBufferedWriter(
                outDirectory.resolve("stats.log"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return new StatsLog(writer);
    }

    void runStarted(AppConfig config) throws IOException {
        write("Run started");
        write("Config: out=" + config.outDirectory()
                + ", minSizeBytes=" + config.minSizeBytes()
                + ", minCommits=" + config.minCommits()
                + ", sizeStepBytes=" + config.sizeStepBytes()
                + ", limit=" + (config.hasLimit() ? config.limit() : "none"));
    }

    void bucketStarted(String query, long start, long end) throws IOException {
        write("");
        write("Bucket started: sizeRange=" + start + ".." + end + ", query=\"" + query + "\"");
    }

    void bucketSearchReported(int totalCount, boolean incomplete) throws IOException {
        write("Bucket reported results=" + totalCount + ", incomplete=" + incomplete);
        if (totalCount >= 1000) {
            write("Bucket warning: GitHub search may cap this bucket at 1,000 results.");
        }
        if (incomplete) {
            write("Bucket warning: GitHub marked this bucket as incomplete.");
        }
    }

    void rangeSplit(SearchSizeRange parent, SearchSizeRange left, SearchSizeRange right, int totalCount) throws IOException {
        write("Bucket split: parent=" + parent
                + ", reportedResults=" + totalCount
                + ", children=" + left + " and " + right);
    }

    void rangeCannotSplit(SearchSizeRange range, int totalCount) throws IOException {
        write("Bucket warning: range=" + range
                + " still reported " + totalCount
                + " result(s), but it cannot be split further by size.");
    }

    void bucketFinished(String query, CollectorStats.Snapshot bucketStats) throws IOException {
        write("Bucket finished: query=\"" + query + "\", " + bucketStats.formatSummary());
        write("Bucket completed scan: " + bucketStats.formatScanCounts());
    }

    void runFinished(CollectorStats stats, boolean stoppedByLimit) throws IOException {
        write("");
        write("--------------------------------------------------------------------------------");
        write("Run finished: stoppedByLimit=" + stoppedByLimit);
        write("Final stats: " + stats.formatSummary());
        write(stats.formatCompletedScan());
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    private void write(String message) throws IOException {
        writer.write("[" + FORMATTER.format(LocalDateTime.now()) + "] " + message);
        writer.newLine();
        writer.flush();
    }
}
