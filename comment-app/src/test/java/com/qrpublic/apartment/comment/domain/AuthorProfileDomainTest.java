package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.service.CoreAuthorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The author snapshot ladder on the name side; both remote lookups point at a
 * dead port here, which is exactly the failure the design must absorb: a
 * profile service outage must never block a comment.
 */
class AuthorProfileDomainTest {

    private CoreAuthorService coreAuthor;
    private AuthorProfileDomain domain;

    @BeforeEach
    void setUp() {
        coreAuthor = Mockito.mock(CoreAuthorService.class);
        // Port 1: connect-refused immediately, both clients time out into
        // their fallbacks.
        domain = new AuthorProfileDomain("http://127.0.0.1:1", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(domain, "coreAuthorService", coreAuthor);
        ReflectionTestUtils.setField(domain, "cacheMinutes", 60L);
    }

    @Test
    void unresolvedProfileFallsBackToTheUsername() {
        when(coreAuthor.findAuthor(any())).thenReturn(Optional.empty());

        AuthorSnapshot snapshot = domain.resolveSnapshot("buyer9");

        assertEquals("buyer9", snapshot.displayName());
        assertNull(snapshot.dataUrl());
        ArgumentCaptor<com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity> cached =
                ArgumentCaptor.forClass(com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity.class);
        verify(coreAuthor).saveAuthor(cached.capture());
        assertEquals("buyer9", cached.getValue().getFullName());
    }

    @Test
    void cachedNameIsReusedWithoutAnyRemoteCall() {
        com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity row =
                new com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity();
        row.setUsername("seller1");
        row.setFullName("Tran Van B");
        row.setDataUrl("data:image/png;base64,AA");
        row.setFetchedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        when(coreAuthor.findAuthor("seller1")).thenReturn(Optional.of(row));

        AuthorSnapshot snapshot = domain.resolveSnapshot("seller1");

        assertEquals("Tran Van B", snapshot.displayName());
        assertEquals("data:image/png;base64,AA", snapshot.dataUrl());
    }

    @Test
    void publicAvatarReadsOnlyTheCache() {
        com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity row =
                new com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity();
        row.setDataUrl("data:image/png;base64,BB");
        when(coreAuthor.findAuthor("seller1")).thenReturn(Optional.of(row));
        when(coreAuthor.findAuthor("nobody")).thenReturn(Optional.empty());

        assertEquals("data:image/png;base64,BB", domain.cachedAvatar("seller1"));
        assertNull(domain.cachedAvatar("nobody"));
    }
}
