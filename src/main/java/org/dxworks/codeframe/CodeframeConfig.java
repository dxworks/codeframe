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
    private static final boolean DEFAULT_HIDE_SQL_TABLE_COLUMNS = false;

    private final int maxFileLines;
    private final boolean hideSqlTableColumns;

    private CodeframeConfig(int maxFileLines, boolean hideSqlTableColumns) {
        this.maxFileLines = maxFileLines;
        this.hideSqlTableColumns = hideSqlTableColumns;
    }

    public int getMaxFileLines() {
        return maxFileLines;
    }

    public boolean isHideSqlTableColumns() {
        return hideSqlTableColumns;
    }

    public static CodeframeConfig load() {
        Path configPath = Paths.get(CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            return new CodeframeConfig(DEFAULT_MAX_FILE_LINES, DEFAULT_HIDE_SQL_TABLE_COLUMNS);
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            YamlConfig yamlConfig = yamlMapper.readValue(configPath.toFile(), YamlConfig.class);
            if (yamlConfig != null) {
                Integer maxFileLines = yamlConfig.maxFileLines;
                Boolean hideSqlTableColumns = yamlConfig.hideSqlTableColumns;

                int effectiveMaxFileLines = (maxFileLines != null && maxFileLines > 0)
                        ? maxFileLines
                        : DEFAULT_MAX_FILE_LINES;
                boolean effectiveHideSqlTableColumns = (hideSqlTableColumns != null)
                        ? hideSqlTableColumns
                        : DEFAULT_HIDE_SQL_TABLE_COLUMNS;

                return new CodeframeConfig(effectiveMaxFileLines, effectiveHideSqlTableColumns);
            }
        } catch (IOException e) {
            // Fall through to default
        }

        return new CodeframeConfig(DEFAULT_MAX_FILE_LINES, DEFAULT_HIDE_SQL_TABLE_COLUMNS);
    }

    public static CodeframeConfig with(int maxFileLines, boolean hideSqlTableColumns) {
        int effectiveMaxFileLines = maxFileLines > 0 ? maxFileLines : DEFAULT_MAX_FILE_LINES;
        return new CodeframeConfig(effectiveMaxFileLines, hideSqlTableColumns);
    }

    private static class YamlConfig {
        public Integer maxFileLines;
        public Boolean hideSqlTableColumns;
    }
}
