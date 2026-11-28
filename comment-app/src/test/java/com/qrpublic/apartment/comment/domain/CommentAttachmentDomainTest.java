package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.AttachmentKind;
import com.qrpublic.apartment.comment.core.model.AttachmentModel;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat-app upload policy, ported: allow-list first, magic bytes second,
 * size caps, and a random storage key that no hostile name can steer.
 */
class CommentAttachmentDomainTest {

    @TempDir
    Path uploadDir;

    private CommentAttachmentDomain domain;

    @BeforeEach
    void setUp() {
        domain = new CommentAttachmentDomain();
        ReflectionTestUtils.setField(domain, "configuredUploadDir", uploadDir.toString());
        ReflectionTestUtils.setField(domain, "maxImageBytes", 5L * 1024 * 1024);
        ReflectionTestUtils.setField(domain, "maxFileBytes", 10L * 1024 * 1024);
    }

    @Test
    void realPngIsStoredUnderARandomKey() throws Exception {
        byte[] png = pngBytes();
        AttachmentModel model = domain.store(png, "screenshot.png", "image/png");

        assertEquals(AttachmentKind.IMAGE, model.getKind());
        assertEquals("image/png", model.getMimeType());
        assertTrue(model.getStorageKey().endsWith(".png"));
        assertTrue(java.nio.file.Files.exists(uploadDir.resolve(model.getStorageKey())),
                "bytes land under the configured root, named by the random key");
    }

    @Test
    void renamedExecutableFailsTheMagicByteCheck() {
        byte[] mzb = new byte[] { 0x4D, 0x5A, 0x01, 0x02, 0x03 };
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.store(mzb, "invoice.png", "image/png"));
        assertEquals(415, error.getErrorCode());
    }

    @Test
    void blockedExtensionIsRefusedOutright() {
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.store("data".getBytes(), "setup.exe", "application/octet-stream"));
        assertEquals(415, error.getErrorCode());
    }

    @Test
    void oversizedImageIsRefused() {
        byte[] big = new byte[6 * 1024 * 1024];
        System.arraycopy(pngBytes(), 0, big, 0, 4);
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.store(big, "huge.png", "image/png"));
        assertEquals(413, error.getErrorCode());
    }

    @Test
    void pathTraversalInNameCannotEscapeTheDirectory() {
        // The name is sanitised to its last segment and the stored key is random,
        // so loading a key that tries to walk out of the root is a plain 404.
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.load("../../etc/passwd"));
        assertEquals(404, error.getErrorCode());
    }

    @Test
    void emptyUploadIsRefused() {
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.store(new byte[0], "x.png", "image/png"));
        assertEquals(400, error.getErrorCode());
    }

    private static byte[] pngBytes() {
        return new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    }
}
