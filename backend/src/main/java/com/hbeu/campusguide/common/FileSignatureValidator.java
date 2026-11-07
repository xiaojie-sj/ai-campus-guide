package com.hbeu.campusguide.common;

import java.nio.charset.StandardCharsets;

/**
 * Rejects extension/content mismatches before a knowledge file is stored.
 */
public final class FileSignatureValidator {
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};

    private FileSignatureValidator() {
    }

    public static void validate(String extension, byte[] bytes) {
        switch (extension) {
            case "pdf" -> {
                if (!containsAscii(bytes, "%PDF-", 1024)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容不是有效的 PDF");
                }
            }
            case "doc" -> {
                if (!startsWith(bytes, OLE)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容不是有效的 DOC");
                }
            }
            case "docx" -> {
                if (!startsWith(bytes, ZIP)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文件内容不是有效的 DOCX");
                }
            }
            case "txt" -> {
                for (byte value : bytes) {
                    if (value == 0) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "文本文件包含二进制内容");
                    }
                }
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的文件类型");
        }
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsAscii(byte[] value, String needle, int maxOffset) {
        byte[] target = needle.getBytes(StandardCharsets.US_ASCII);
        int limit = Math.min(value.length - target.length, maxOffset);
        for (int i = 0; i <= limit; i++) {
            boolean matches = true;
            for (int j = 0; j < target.length; j++) {
                if (value[i + j] != target[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
