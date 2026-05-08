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

final class UnclassifiedRepositories {
    static final String HEADER = "repository_name,reason";

    private final Path file;
    private final Set<String> repositories;

    private UnclassifiedRepositories(Path file, Set<String> repositories) {
        this.file = file;
        this.repositories = repositories;
    }

    static UnclassifiedRepositories open(Path outDirectory) throws IOException {
        Files.createDirectories(outDirectory);
        Path file = outDirectory.resolve("unclassified-repositories.csv");
        if (Files.notExists(file)) {
            Files.writeString(file, HEADER + System.lineSeparator(), StandardCharsets.UTF_8);
            return new UnclassifiedRepositories(file, new HashSet<>());
        }

        Set<String> repositories = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            List<String> fields = CsvCatalog.parseCsvLine(lines.get(i));
            if (!fields.isEmpty() && !fields.getFirst().isBlank()) {
                repositories.add(fields.getFirst());
            }
        }
        return new UnclassifiedRepositories(file, repositories);
    }

    boolean contains(String repositoryName) {
        return repositories.contains(repositoryName);
    }

    void add(String repositoryName, String reason) throws IOException {
        if (repositories.add(repositoryName)) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(CsvCatalog.escape(repositoryName));
                writer.write(',');
                writer.write(CsvCatalog.escape(reason == null ? "" : reason));
                writer.newLine();
            }
        }
    }
}
