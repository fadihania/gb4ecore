package com.fadihania;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubFileKeyTest {
    @Test
    void branchAndCommitShaUrlsForSamePathProduceSameKey() {
        Optional<GitHubFileKey> branch = GitHubFileKey.fromBlobUrl(
                "https://github.com/owner/repo/blob/main/model/example.ecore");
        Optional<GitHubFileKey> commit = GitHubFileKey.fromBlobUrl(
                "https://github.com/owner/repo/blob/d67582e93f7d3fefed1158a7d32580c2e3d745f2/model/example.ecore");

        assertTrue(branch.isPresent());
        assertEquals(branch, commit);
        assertEquals("owner/repo|model/example.ecore", branch.orElseThrow().toString());
    }

    @Test
    void differentPathsProduceDifferentKeys() {
        GitHubFileKey first = GitHubFileKey.fromBlobUrl(
                "https://github.com/owner/repo/blob/main/model/first.ecore").orElseThrow();
        GitHubFileKey second = GitHubFileKey.fromBlobUrl(
                "https://github.com/owner/repo/blob/main/model/second.ecore").orElseThrow();

        assertNotEquals(first, second);
    }

    @Test
    void invalidUrlsReturnEmptyKey() {
        assertTrue(GitHubFileKey.fromBlobUrl("https://example.com/file.ecore").isEmpty());
        assertTrue(GitHubFileKey.fromBlobUrl(null).isEmpty());
    }
}
