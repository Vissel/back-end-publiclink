package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.AttachmentKind;
import com.qrpublic.apartment.comment.core.model.AttachmentModel;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Binary payloads of comments: what may enter a comment, and where the bytes
 * live. The policy is chat-app's, ported unchanged: an ALLOW-LIST (PNG/JPEG/
 * WebP/GIF images; PDF/TXT/CSV/Word/Excel/PowerPoint documents), magic-byte
 * verification of the actual bytes, a no-NUL check for text — executables,
 * scripts, archives and SVG (script-bearing) are refused outright.
 *
 * Bytes are written under {@code comment.upload-dir} named by a random storage
 * key; the sender's file name is kept sanitised for display only, so a hostile
 * name can never become a path. One deliberate difference from chat: comment
 * attachments are publicly downloadable by their unguessable uuid, because
 * comment reading is public on sale-link pages.
 */
@Slf4j
@Component
public class CommentAttachmentDomain {

    /** Images render inline under the comment. */
    private static final Map<String, AttachmentKind> IMAGE_EXTENSIONS = Map.of(
            "png", AttachmentKind.IMAGE,
            "jpg", AttachmentKind.IMAGE,
            "jpeg", AttachmentKind.IMAGE,
            "webp", AttachmentKind.IMAGE,
            "gif", AttachmentKind.IMAGE);

    /** Standard office/document attachments, offered as a download row. */
    private static final Map<String, AttachmentKind> FILE_EXTENSIONS = Map.of(
            "pdf", AttachmentKind.FILE,
            "txt", AttachmentKind.FILE,
            "csv", AttachmentKind.FILE,
            "doc", AttachmentKind.FILE,
            "docx", AttachmentKind.FILE,
            "xls", AttachmentKind.FILE,
            "xlsx", AttachmentKind.FILE,
            "ppt", AttachmentKind.FILE,
            "pptx", AttachmentKind.FILE);

    private static final Map<String, String> IMAGE_MIMES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif");

    private static final Map<String, String> MIME_EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final int MAX_NAME_LENGTH = 200;
    private static final int TEXT_SNIFF_BYTES = 8192;

    @Value("${comment.upload-dir:}")
    private String configuredUploadDir;

    @Value("${comment.max-image-bytes:5242880}")
    private long maxImageBytes;

    @Value("${comment.max-file-bytes:10485760}")
    private long maxFileBytes;

    /**
     * Validates one upload and writes it to disk. The returned model has no
     * comment id yet: the caller stores the comment first, then the metadata
     * row through the core layer, so a refused upload never leaves a
     * half comment behind.
     */
    public AttachmentModel store(byte[] bytes, String originalName, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new ApplicationException("The uploaded file is empty.", 400);
        }
        String fileName = sanitizeName(originalName, contentType);
        String extension = extensionOf(fileName);
        AttachmentKind kind = kindOf(extension);

        long limit = kind == AttachmentKind.IMAGE ? maxImageBytes : maxFileBytes;
        if (bytes.length > limit) {
            throw new ApplicationException("The file is larger than the " + humanLimit(limit) + " limit.", 413);
        }
        verifyMagic(kind, extension, bytes);

        String storageKey = UUID.randomUUID() + "." + extension;
        try {
            Files.write(resolveNew(storageKey), bytes);
        } catch (IOException e) {
            log.error("Unable to store comment attachment {}: {}", storageKey, e.getMessage());
            throw new ApplicationException("Unable to store the uploaded file.", 500);
        }

        AttachmentModel model = new AttachmentModel();
        model.setKind(kind);
        model.setFileName(fileName);
        model.setMimeType(mimeType(kind, extension, contentType));
        model.setSizeBytes((long) bytes.length);
        model.setStorageKey(storageKey);
        log.info("Comment attachment stored: key={}, kind={}, bytes={}", storageKey, kind, bytes.length);
        return model;
    }

    /**
     * Reads the bytes back for a download; a missing file is a 404, not a crash.
     */
    public byte[] load(String storageKey) {
        Path file = resolveExisting(storageKey);
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("Unable to read comment attachment {}: {}", storageKey, e.getMessage());
            throw new ApplicationException("Unable to read the stored file.", 500);
        }
    }

    // ------------------------------------------------------------------ policy

    private static AttachmentKind kindOf(String extension) {
        AttachmentKind kind = IMAGE_EXTENSIONS.get(extension);
        if (kind == null) {
            kind = FILE_EXTENSIONS.get(extension);
        }
        if (kind == null) {
            throw new ApplicationException(
                    "This file type is not allowed. Images: PNG, JPEG, WebP, GIF. Documents: PDF, TXT, CSV, Word, Excel, PowerPoint.",
                    415);
        }
        return kind;
    }

    /**
     * Container formats must prove their type: an .exe renamed to .png or .pdf
     * is refused here, before anything touches the disk.
     */
    private static void verifyMagic(AttachmentKind kind, String extension, byte[] bytes) {
        switch (extension) {
            case "png":
                require(startsWith(bytes, 0x89, 0x50, 0x4E, 0x47), extension);
                break;
            case "jpg":
            case "jpeg":
                require(startsWith(bytes, 0xFF, 0xD8, 0xFF), extension);
                break;
            case "gif":
                require(startsWithAscii(bytes, "GIF8"), extension);
                break;
            case "webp":
                require(startsWithAscii(bytes, "RIFF") && bytes.length > 11
                        && startsWithAsciiAt(bytes, 8, "WEBP"), extension);
                break;
            case "pdf":
                require(startsWithAscii(bytes, "%PDF"), extension);
                break;
            case "doc":
            case "xls":
            case "ppt":
                require(startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0), extension);
                break;
            case "docx":
            case "xlsx":
            case "pptx":
                require(startsWith(bytes, 0x50, 0x4B, 0x03, 0x04), extension);
                break;
            default:
                requireText(bytes, extension);
                break;
        }
    }

    private static void requireText(byte[] bytes, String extension) {
        int scan = Math.min(bytes.length, TEXT_SNIFF_BYTES);
        for (int i = 0; i < scan; i++) {
            if (bytes[i] == 0) {
                throw new ApplicationException("The file content does not match its extension: " + extension, 415);
            }
        }
    }

    private static void require(boolean ok, String extension) {
        if (!ok) {
            throw new ApplicationException("The file content does not match its extension: " + extension, 415);
        }
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] bytes, String prefix) {
        return startsWithAsciiAt(bytes, 0, prefix);
    }

    private static boolean startsWithAsciiAt(byte[] bytes, int offset, String prefix) {
        if (bytes.length < offset + prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (bytes[offset + i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------- names

    /**
     * Strips everything a file name could smuggle in (path separators, control
     * chars); a paste without a name falls back to one derived from its MIME.
     */
    private static String sanitizeName(String originalName, String contentType) {
        String base = originalName == null ? "" : originalName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\p{Cntrl}]", "").trim();
        if (base.length() > MAX_NAME_LENGTH) {
            base = base.substring(0, MAX_NAME_LENGTH);
        }
        if (!base.isEmpty() && IMAGE_EXTENSIONS.containsKey(extensionOf(base))) {
            return base;
        }
        if (!base.isEmpty() && FILE_EXTENSIONS.containsKey(extensionOf(base))) {
            return base;
        }
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        String fromMime = MIME_EXTENSIONS.get(mime);
        if (fromMime != null) {
            return "image." + fromMime;
        }
        if (!base.isEmpty()) {
            return base;
        }
        throw new ApplicationException("The uploaded file has no usable name.", 400);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeType(AttachmentKind kind, String extension, String contentType) {
        if (kind == AttachmentKind.IMAGE) {
            return IMAGE_MIMES.getOrDefault(extension, "application/octet-stream");
        }
        if ("pdf".equals(extension)) {
            return "application/pdf";
        }
        if ("txt".equals(extension) || "csv".equals(extension)) {
            return "text/plain";
        }
        // Office MIME types vary by browser; the stored one is only a hint for
        // the download header, and documents are always served as downloads.
        return contentType == null || contentType.isBlank() ? "application/octet-stream"
                : contentType.split(";")[0].trim();
    }

    private static String humanLimit(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    // ----------------------------------------------------------------- storage

    private Path root() {
        String base = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? System.getProperty("java.io.tmpdir") + "/publiclink-comment-uploads"
                : configuredUploadDir.trim();
        Path dir = Paths.get(base).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApplicationException("Comment upload directory is not writable: " + dir, 500);
        }
        return dir;
    }

    private Path resolveNew(String storageKey) {
        return root().resolve(storageKey).normalize();
    }

    private Path resolveExisting(String storageKey) {
        Path dir = root();
        Path file = dir.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new ApplicationException("Attachment not found on disk.", 404);
        }
        return file;
    }
}
