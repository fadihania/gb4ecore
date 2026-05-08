package com.fadihania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnclassifiedRepositoriesTest {
    @TempDir
    Path tempDir;

    @Test
    void createsHeaderWhenMissing() throws Exception {
        UnclassifiedRepositories.open(tempDir);

        String content = Files.readString(tempDir.resolve("unclassified-repositories.csv"), StandardCharsets.UTF_8);

        assertTrue(content.startsWith(UnclassifiedRepositories.HEADER));
    }

    @Test
    void appendsRepositoryAndReasonWithCsvEscaping() throws Exception {
        UnclassifiedRepositories repositories = UnclassifiedRepositories.open(tempDir);

        repositories.add("owner/repo", "failed, because \"GitHub\"");

        String content = Files.readString(tempDir.resolve("unclassified-repositories.csv"), StandardCharsets.UTF_8);

        assertTrue(content.contains("owner/repo,\"failed, because \"\"GitHub\"\"\""));
    }

    @Test
    void avoidsDuplicateRepositoryRowsAfterReload() throws Exception {
        UnclassifiedRepositories repositories = UnclassifiedRepositories.open(tempDir);
        repositories.add("owner/repo", "first");

        UnclassifiedRepositories reloaded = UnclassifiedRepositories.open(tempDir);
        reloaded.add("owner/repo", "second");

        long rows = Files.readAllLines(tempDir.resolve("unclassified-repositories.csv"), StandardCharsets.UTF_8)
                .stream()
                .filter(line -> line.startsWith("owner/repo"))
                .count();

        assertEquals(1, rows);
    }
}
