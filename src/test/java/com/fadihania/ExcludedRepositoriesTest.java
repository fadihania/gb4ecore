package com.fadihania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcludedRepositoriesTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsAndReloadsRepositories() throws Exception {
        ExcludedRepositories repositories = ExcludedRepositories.open(tempDir);

        assertFalse(repositories.contains("owner/repo"));
        repositories.add("owner/repo");
        repositories.add("owner/repo");

        ExcludedRepositories reloaded = ExcludedRepositories.open(tempDir);
        assertTrue(reloaded.contains("owner/repo"));
    }
}
