package com.fadihania;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvCatalogTest {
    @TempDir
    Path tempDir;

    @Test
    void escapesCsvValues() {
        ModelRecord record = new ModelRecord(
                "repo_model.ecore",
                "owner/repo",
                "https://github.com/owner/repo",
                "https://github.com/owner/repo/blob/main/model.ecore",
                12_345,
                37);

        assertEquals(
                "repo_model.ecore,owner/repo,https://github.com/owner/repo,https://github.com/owner/repo/blob/main/model.ecore,12345,37",
                CsvCatalog.toCsvLine(record));

        assertEquals("\"a,b\"\"c\"", CsvCatalog.escape("a,b\"c"));
    }

    @Test
    void resumesFromExistingCsv() throws Exception {
        Path csv = tempDir.resolve("ecore-models.csv");
        Files.writeString(csv,
                CsvCatalog.HEADER + System.lineSeparator()
                        + "model.ecore,owner/repo,repo-url,file-url,10241,10" + System.lineSeparator(),
                StandardCharsets.UTF_8);

        CsvCatalog catalog = CsvCatalog.open(tempDir);

        assertTrue(catalog.containsFileUrl("file-url"));
    }

    @Test
    void writesHeaderAndAppendsRows() throws Exception {
        CsvCatalog catalog = CsvCatalog.open(tempDir);
        catalog.append(new ModelRecord("model.ecore", "owner/repo", "repo-url", "file-url", 10241, 10));

        String content = Files.readString(tempDir.resolve("ecore-models.csv"), StandardCharsets.UTF_8);

        assertTrue(content.startsWith(CsvCatalog.HEADER));
        assertTrue(content.contains("model.ecore,owner/repo,repo-url,file-url,10241,10"));
    }

    @Test
    void serializesTotalCommitCountsGreaterThanThreshold() {
        ModelRecord record = new ModelRecord("model.ecore", "owner/repo", "repo-url", "file-url", 10241, 37);

        assertTrue(CsvCatalog.toCsvLine(record).endsWith(",37"));
    }
}
