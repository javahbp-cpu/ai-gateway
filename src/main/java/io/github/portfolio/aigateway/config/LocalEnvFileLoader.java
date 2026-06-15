package io.github.portfolio.aigateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class LocalEnvFileLoader {

    private static final String ENV_FILE_NAME = ".env";

    private LocalEnvFileLoader() {
    }

    public static void load() {
        load(Path.of(ENV_FILE_NAME));
    }

    static int load(Path envFile) {
        if (!Files.isRegularFile(envFile)) {
            return 0;
        }
        try {
            return loadLines(Files.readAllLines(envFile, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static int loadLines(List<String> lines) {
        int loaded = 0;
        for (String line : lines) {
            String normalized = normalize(line);
            if (normalized.isBlank() || normalized.startsWith("#")) {
                continue;
            }
            int separator = normalized.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = normalized.substring(0, separator).trim();
            String value = unquote(normalized.substring(separator + 1).trim());
            // 本地 .env 只作为开发兜底，不覆盖系统环境变量或 JVM 启动参数。
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
                loaded++;
            }
        }
        return loaded;
    }

    private static String normalize(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("export ")) {
            return trimmed.substring("export ".length()).trim();
        }
        return trimmed;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
