package com.fadihania;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record GitHubFileKey(String repositoryName, String path) {
    private static final Pattern BLOB_URL = Pattern.compile(
            "^https://github\\.com/([^/]+/[^/]+)/blob/[^/]+/(.+)$",
            Pattern.CASE_INSENSITIVE);

    GitHubFileKey {
        repositoryName = repositoryName.toLowerCase(Locale.ROOT);
        path = path.toLowerCase(Locale.ROOT);
    }

    static Optional<GitHubFileKey> fromBlobUrl(String url) {
        if (url == null) {
            return Optional.empty();
        }
        Matcher matcher = BLOB_URL.matcher(url);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new GitHubFileKey(matcher.group(1), matcher.group(2)));
    }

    @Override
    public String toString() {
        return repositoryName + "|" + path;
    }
}
