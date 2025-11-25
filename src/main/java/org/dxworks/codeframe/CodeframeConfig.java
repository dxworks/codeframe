package org.dxworks.codeframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CodeframeConfig {

    private static final int DEFAULT_MAX_FILE_LINES = 20000;
    private static final String CONFIG_FILE_NAME = "codeframe-config.yml";

    private final int maxFileLines;

    private CodeframeConfig(int maxFileLines) {
        this.maxFileLines = maxFileLines;
    }

    public int getMaxFileLines() {
        return maxFileLines;
    }

    public static CodeframeConfig load() {
        Path configPath = Paths.get(CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            return new CodeframeConfig(DEFAULT_MAX_FILE_LINES);
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            YamlConfig yamlConfig = yamlMapper.readValue(configPath.toFile(), YamlConfig.class);
            if (yamlConfig != null && yamlConfig.maxFileLines != null && yamlConfig.maxFileLines > 0) {
                return new CodeframeConfig(yamlConfig.maxFileLines);
            }
        } catch (IOException e) {
            // Fall through to default
        }

        return new CodeframeConfig(DEFAULT_MAX_FILE_LINES);
    }

    private static class YamlConfig {
        public Integer maxFileLines;
    }
}
