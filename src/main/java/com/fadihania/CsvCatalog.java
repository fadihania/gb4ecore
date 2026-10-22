package com.fadihania;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

final class CsvCatalog {
    static final String HEADER = "model_file_name,repository_name,repository_url,full_file_url,file_size,commits";

    private final Path csvFile;
    private final Set<String> existingFileUrls;
    private final Set<GitHubFileKey> existingFileKeys;
    private final Map<String, ExistingModel> existingModelsByUrl;
    private final Map<GitHubFileKey, ExistingModel> existingModelsByKey;

    private CsvCatalog(
            Path csvFile,
            Set<String> existingFileUrls,
            Set<GitHubFileKey> existingFileKeys,
            Map<String, ExistingModel> existingModelsByUrl,
            Map<GitHubFileKey, ExistingModel> existingModelsByKey
    ) {
        this.csvFile = csvFile;
        this.existingFileUrls = existingFileUrls;
        this.existingFileKeys = existingFileKeys;
        this.existingModelsByUrl = existingModelsByUrl;
        this.existingModelsByKey = existingModelsByKey;
    }

    static CsvCatalog open(Path outDirectory) throws IOException {
        Files.createDirectories(outDirectory);
        Path csv = outDirectory.resolve("ecore-models.csv");
        if (Files.notExists(csv)) {
            Files.writeString(csv, HEADER + System.lineSeparator(), StandardCharsets.UTF_8);
            return new CsvCatalog(csv, new HashSet<>(), new HashSet<>(), new HashMap<>(), new HashMap<>());
        }

        Set<String> urls = new HashSet<>();
        Set<GitHubFileKey> keys = new HashSet<>();
        Map<String, ExistingModel> modelsByUrl = new HashMap<>();
        Map<GitHubFileKey, ExistingModel> modelsByKey = new HashMap<>();
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = parseCsvLine(lines.get(i));
            if (fields.size() >= 4) {
                ExistingModel model = existingModel(fields);
                addExistingUrl(urls, keys, modelsByUrl, modelsByKey, fields.get(3), model);
            }
        }
        return new CsvCatalog(csv, urls, keys, modelsByUrl, modelsByKey);
    }

    boolean containsFileUrl(String fileUrl) {
        return existingFileUrls.contains(fileUrl)
                || GitHubFileKey.fromBlobUrl(fileUrl)
                .map(existingFileKeys::contains)
                .orElse(false);
    }

    Optional<ExistingModel> findExistingModel(String fileUrl) {
        ExistingModel byUrl = existingModelsByUrl.get(fileUrl);
        if (byUrl != null) {
            return Optional.of(byUrl);
        }
        return GitHubFileKey.fromBlobUrl(fileUrl)
                .map(existingModelsByKey::get);
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
        ExistingModel model = new ExistingModel(record.fileSize(), record.totalCommits());
        addExistingUrl(existingFileUrls, existingFileKeys, existingModelsByUrl, existingModelsByKey, record.fullFileUrl(), model);
    }

    private static void addExistingUrl(
            Set<String> urls,
            Set<GitHubFileKey> keys,
            Map<String, ExistingModel> modelsByUrl,
            Map<GitHubFileKey, ExistingModel> modelsByKey,
            String url,
            ExistingModel model
    ) {
        urls.add(url);
        if (model != null) {
            modelsByUrl.put(url, model);
        }
        GitHubFileKey.fromBlobUrl(url).ifPresent(key -> {
            keys.add(key);
            if (model != null) {
                modelsByKey.put(key, model);
            }
        });
    }

    private static ExistingModel existingModel(List<String> fields) {
        if (fields.size() < 6) {
            return null;
        }
        try {
            return new ExistingModel(Long.parseLong(fields.get(4)), Integer.parseInt(fields.get(5)));
        } catch (NumberFormatException e) {
            return null;
        }
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

    record ExistingModel(long fileSize, int totalCommits) {
    }
}
