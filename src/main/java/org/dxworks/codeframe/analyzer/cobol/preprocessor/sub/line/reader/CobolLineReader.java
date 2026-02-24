/*
 * Copyright (C) 2017, Ulrich Wolffgang <u.wol@wwu.de>
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the BSD 3-clause license. See the LICENSE file for details.
 */

package org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.line.reader;

import java.util.List;

import org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor.CobolDialect;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.CobolLine;

/**
 * Preprocessor, which analyzes and processes line indicators.
 */
public interface CobolLineReader {

	CobolLine parseLine(String line, int lineNumber, CobolSourceFormatEnum format, CobolDialect dialect);

	List<CobolLine> processLines(String lines, CobolSourceFormatEnum format, CobolDialect dialect);

}
