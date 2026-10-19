package org.dxworks.codeframe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class SourceCodeReader {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private SourceCodeReader() {
    }

    static String read(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);

        if (hasUtf8Bom(bytes)) {
            return stripLeadingBom(new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8));
        }
        if (hasUtf16LeBom(bytes)) {
            return stripLeadingBom(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE));
        }
        if (hasUtf16BeBom(bytes)) {
            return stripLeadingBom(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE));
        }

        List<Charset> charsetFallbacks = List.of(
                StandardCharsets.UTF_8,
                WINDOWS_1252,
                StandardCharsets.ISO_8859_1
        );

        CharacterCodingException lastException = null;
        for (Charset charset : charsetFallbacks) {
            try {
                return stripLeadingBom(decodeStrict(bytes, charset));
            } catch (CharacterCodingException e) {
                lastException = e;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IllegalStateException("Unable to decode file: " + filePath);
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String stripLeadingBom(String text) {
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static boolean hasUtf16LeBom(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xFE;
    }

    private static boolean hasUtf16BeBom(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[0] & 0xFF) == 0xFE
                && (bytes[1] & 0xFF) == 0xFF;
    }
}
