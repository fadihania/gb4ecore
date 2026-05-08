package com.fadihania;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryCommitPrecheckTest {
    @Test
    void fewerRepositoryCommitsThanMinimumCannotHaveMatchingFile() {
        RepositoryCommitPrecheck precheck = RepositoryCommitPrecheck.fromCommitsChecked(5, 10);

        assertFalse(precheck.canHaveMatchingFile());
    }

    @Test
    void enoughRepositoryCommitsCanHaveMatchingFile() {
        RepositoryCommitPrecheck precheck = RepositoryCommitPrecheck.fromCommitsChecked(10, 10);

        assertTrue(precheck.canHaveMatchingFile());
    }
}
