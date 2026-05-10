package com.fadihania;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CsvCatalog {
    static final String HEADER = "model_file_name,repository_name,repository_url,full_file_url,file_size,commits";

    private final Path csvFile;
    private final Set<String> existingFileUrls;
    private final Set<GitHubFileKey> existingFileKeys;

    private CsvCatalog(Path csvFile, Set<String> existingFileUrls, Set<GitHubFileKey> existingFileKeys) {
        this.csvFile = csvFile;
        this.existingFileUrls = existingFileUrls;
        this.existingFileKeys = existingFileKeys;
    }

    static CsvCatalog open(Path outDirectory) throws IOException {
        Files.createDirectories(outDirectory);
        Path csv = outDirectory.resolve("ecore-models.csv");
        if (Files.notExists(csv)) {
            Files.writeString(csv, HEADER + System.lineSeparator(), StandardCharsets.UTF_8);
            return new CsvCatalog(csv, new HashSet<>(), new HashSet<>());
        }

        Set<String> urls = new HashSet<>();
        Set<GitHubFileKey> keys = new HashSet<>();
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            if (fields.size() >= 4) {
                addExistingUrl(urls, keys, fields.get(3));
            }
        }
        return new CsvCatalog(csv, urls, keys);
    }

    boolean containsFileUrl(String fileUrl) {
        return existingFileUrls.contains(fileUrl)
                || GitHubFileKey.fromBlobUrl(fileUrl)
                .map(existingFileKeys::contains)
                .orElse(false);
    }

    void append(ModelRecord record) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                csvFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(toCsvLine(record));
            writer.newLine();
        }
        addExistingUrl(existingFileUrls, existingFileKeys, record.fullFileUrl());
    }

    private static void addExistingUrl(Set<String> urls, Set<GitHubFileKey> keys, String url) {
        urls.add(url);
        GitHubFileKey.fromBlobUrl(url).ifPresent(keys::add);
    }

    static String toCsvLine(ModelRecord record) {
        return String.join(",",
                escape(record.modelFileName()),
                escape(record.repositoryName()),
                escape(record.repositoryUrl()),
                escape(record.fullFileUrl()),
                Long.toString(record.fileSize()),
                Integer.toString(record.totalCommits()));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    static List<String> parseCsvLine(String line) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
