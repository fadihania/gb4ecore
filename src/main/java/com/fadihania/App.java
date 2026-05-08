package com.fadihania;

import java.io.IOException;
import java.util.Map;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.getenv());
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, Map<String, String> environment) {
        CommandLineLogger logger = new CommandLineLogger();
        try {
            AppConfig config = AppConfig.parse(args);
            String token = environment.get("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                logger.error("GITHUB_TOKEN is required. Create a GitHub token and set it in the environment before running.");
                return 2;
            }

            GitHubEcoreCollector collector = GitHubEcoreCollector.connect(config, token, logger);
            collector.run();
            return 0;
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            logger.info("Usage: mvn exec:java -Dexec.args=\"--out out --min-size 10240 --min-commits 10 --size-step 10240 --limit 10\"");
            return 2;
        } catch (IOException e) {
            logger.error("Collector failed: " + e.getMessage());
            return 1;
        }
    }
}
