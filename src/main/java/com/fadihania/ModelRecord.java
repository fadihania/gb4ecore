package com.fadihania;

record ModelRecord(
        String modelFileName,
        String repositoryName,
        String repositoryUrl,
        String fullFileUrl,
        long fileSize,
        int totalCommits
) {
}
