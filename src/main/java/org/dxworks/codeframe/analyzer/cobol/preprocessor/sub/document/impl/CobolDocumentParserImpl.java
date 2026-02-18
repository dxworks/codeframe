/*
 * Copyright (C) 2017, Ulrich Wolffgang <u.wol@wwu.de>
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the BSD 3-clause license. See the LICENSE file for details.
 */

package org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.document.impl;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85PreprocessorLexer;
import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85PreprocessorParser;
import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85PreprocessorParser.StartRuleContext;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor.CobolDialect;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.document.CobolDocumentParser;

/**
 * Preprocessor, which parses and processes COPY REPLACE and EXEC SQL
 * statements.
 */
public class CobolDocumentParserImpl implements CobolDocumentParser {

	protected final List<File> copyFiles;

	private List<String> copyStatements = Collections.emptyList();

	protected final String[] triggers = new String[] { "copy", "exec sql", "exec sqlims", "exec cics", "replace" };

	public CobolDocumentParserImpl(final List<File> copyFiles) {
		this.copyFiles = copyFiles;
	}

	private void debugLog(String message) {
		// Intentionally no-op.
	}

	protected boolean containsTrigger(final String code, final String[] triggers) {
		debugLog("DEBUG: containsTrigger called with code length: " + code.length());
		debugLog("DEBUG: First 200 chars: " + (code.length() > 200 ? code.substring(0, 200) + "..." : code));
		final String[] lines = code.split("\\r?\\n");
		debugLog("DEBUG: Total lines to check: " + lines.length);
		
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i];
			final String trimmedLine = line.trim();
			
			// Skip comment lines - any line starting with comment tags
			if (trimmedLine.length() > 0) {
				if (trimmedLine.startsWith(CobolPreprocessor.COMMENT_TAG) || 
					trimmedLine.startsWith(CobolPreprocessor.COMMENT_ENTRY_TAG)) {
					debugLog("DEBUG: Skipping comment line " + (i+1) + ": '" + trimmedLine + "'");
					continue;
				}
			}
			
			// Skip empty lines
			if (trimmedLine.isEmpty()) {
				debugLog("DEBUG: Skipping empty line " + (i+1));
				continue;
			}
			
			// Debug: print the line being checked
			debugLog("DEBUG: Checking line " + (i+1) + ": '" + trimmedLine + "'");
			
			final String lineLowerCase = trimmedLine.toLowerCase();
			for (final String trigger : triggers) {
				if (lineLowerCase.contains(trigger)) {
					debugLog("DEBUG: Found trigger '" + trigger + "' in line " + (i+1) + ": '" + trimmedLine + "'");
					return true;
				}
			}
		}
		
		debugLog("DEBUG: No triggers found in any lines");
		return false;
	}

	@Override
	public String processLines(final String code, final CobolSourceFormatEnum format, final CobolDialect dialect) {
		debugLog("DEBUG: processLines called");
		final boolean requiresProcessorExecution = containsTrigger(code, triggers);
		debugLog("DEBUG: requiresProcessorExecution = " + requiresProcessorExecution);
		final String result;

		if (requiresProcessorExecution) {
			debugLog("DEBUG: Going to processWithParser - this is where the error occurs");
			result = processWithParser(code, copyFiles, format, dialect);
		} else {
			debugLog("DEBUG: No processing needed, returning original code");
			copyStatements = Collections.emptyList();
			result = code;
		}

		return result;
	}

	protected String processWithParser(final String code, final List<File> copyFiles,
			final CobolSourceFormatEnum format, final CobolDialect dialect) {
		debugLog("DEBUG: processWithParser called with code length: " + code.length());
		// Show first 500 chars of code being sent to parser
		debugLog("DEBUG: Code to parser (first 500 chars): " + (code.length() > 500 ? code.substring(0, 500) + "..." : code));
		
		// run the lexer
		final Cobol85PreprocessorLexer lexer = new Cobol85PreprocessorLexer(CharStreams.fromString(code));

		// get a list of matched tokens
		final CommonTokenStream tokens = new CommonTokenStream(lexer);

		// pass the tokens to the parser
		final Cobol85PreprocessorParser parser = new Cobol85PreprocessorParser(tokens);

		// register an error listener, so that preprocessing stops on errors
		parser.removeErrorListeners();
		parser.addErrorListener(new ThrowingErrorListener());

		// specify our entry point
		final StartRuleContext startRule = parser.startRule();

		// analyze contained copy books
		final CobolDocumentParserListenerImpl listener = new CobolDocumentParserListenerImpl(copyFiles, format, dialect,
				tokens);
		final ParseTreeWalker walker = new ParseTreeWalker();

		walker.walk(listener, startRule);
		copyStatements = listener.copyStatements();

		final String result = listener.context().read();
		return result;
	}

	public List<String> copyStatements() {
		return copyStatements;
	}
}
