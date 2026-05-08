package com.fadihania;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ExcludedRepositories {
    private final Path file;
    private final Set<String> repositories;

    private ExcludedRepositories(Path file, Set<String> repositories) {
        this.file = file;
        this.repositories = repositories;
    }

    static ExcludedRepositories open(Path outDirectory) throws IOException {
        Files.createDirectories(outDirectory);
        Path file = outDirectory.resolve("excluded-repositories.txt");
        if (Files.notExists(file)) {
            Files.createFile(file);
            return new ExcludedRepositories(file, new HashSet<>());
        }

        Set<String> repositories = new HashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                repositories.add(trimmed);
            }
        }
        return new ExcludedRepositories(file, repositories);
    }

    boolean contains(String repositoryName) {
        return repositories.contains(repositoryName);
    }

    void add(String repositoryName) throws IOException {
        if (repositories.add(repositoryName)) {
            Files.write(file, List.of(repositoryName), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }
}
