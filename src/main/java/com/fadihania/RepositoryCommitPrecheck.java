package com.fadihania;

record RepositoryCommitPrecheck(int commitsChecked, boolean canHaveMatchingFile) {
    static RepositoryCommitPrecheck fromCommitsChecked(int commitsChecked, int minimumCommits) {
        return new RepositoryCommitPrecheck(commitsChecked, commitsChecked >= minimumCommits);
    }
}
