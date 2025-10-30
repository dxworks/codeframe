package org.dxworks.codeframe.model;

/**
 * Marker interface for all analysis result types.
 * Allows different languages to return different analysis structures.
 */
public interface Analysis {
    String getFilePath();
    String getLanguage();
}
