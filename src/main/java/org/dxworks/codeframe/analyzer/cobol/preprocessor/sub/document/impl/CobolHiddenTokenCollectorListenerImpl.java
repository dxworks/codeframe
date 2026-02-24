/*
 * Copyright (C) 2017, Ulrich Wolffgang <u.wol@wwu.de>
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the BSD 3-clause license. See the LICENSE file for details.
 */

package org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.document.impl;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;

import org.dxworks.codeframe.analyzer.cobol.generated.Cobol85PreprocessorBaseListener;
import org.dxworks.codeframe.analyzer.cobol.preprocessor.sub.util.TokenUtils;

/**
 * ANTLR listener, which collects visible as well as hidden tokens for a given
 * parse tree in a string buffer.
 */
public class CobolHiddenTokenCollectorListenerImpl extends Cobol85PreprocessorBaseListener {

	boolean firstTerminal = true;

	private final StringBuffer outputBuffer = new StringBuffer();

	private final BufferedTokenStream tokens;

	public CobolHiddenTokenCollectorListenerImpl(final BufferedTokenStream tokens) {
		this.tokens = tokens;
	}

	public String read() {
		return outputBuffer.toString();
	}

	@Override
	public void visitTerminal(final TerminalNode node) {
		if (!firstTerminal) {
			final int tokPos = node.getSourceInterval().a;
			outputBuffer.append(TokenUtils.getHiddenTokensToLeft(tokPos, tokens));
		}

		if (!TokenUtils.isEOF(node)) {
			final String text = node.getText();
			outputBuffer.append(text);
		}

		firstTerminal = false;
	}
}
