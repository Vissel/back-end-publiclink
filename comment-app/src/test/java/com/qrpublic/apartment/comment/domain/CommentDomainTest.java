package com.qrpublic.apartment.comment.domain;

import com.qrpublic.apartment.comment.core.model.AttachmentKind;
import com.qrpublic.apartment.comment.core.model.AttachmentModel;
import com.qrpublic.apartment.comment.core.model.CommentTargetType;
import com.qrpublic.apartment.comment.core.model.CommentView;
import com.qrpublic.apartment.comment.core.service.CoreCommentService;
import com.qrpublic.apartment.comment.entity.CommentEntity;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two ordering rules of the requirement (newest root first; a reply lifts
 * its whole tree) plus the author-only write rules, on a fake store.
 */
class CommentDomainTest {

    private final List<CommentEntity> rows = new ArrayList<>();
    private CoreCommentService core;
    private CommentAttachmentDomain attachments;
    private AuthorProfileDomain authors;
    private CommentDomain domain;
    private long nextId = 1L;

    @BeforeEach
    void setUp() {
        core = Mockito.mock(CoreCommentService.class);
        attachments = Mockito.mock(CommentAttachmentDomain.class);
        authors = Mockito.mock(AuthorProfileDomain.class);

        when(core.save(any(CommentEntity.class))).thenAnswer(invocation -> {
            CommentEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(nextId++);
            }
            if (!rows.contains(entity)) {
                rows.add(entity);
            }
            return entity;
        });
        when(core.findByUuid(any())).thenAnswer(invocation -> rows.stream()
                .filter(row -> row.getUuid() != null && row.getUuid().equals(invocation.getArgument(0)))
                .findFirst());
        when(core.findByIds(anyList())).thenAnswer(invocation -> rows.stream()
                .filter(row -> ((List<Long>) invocation.getArgument(0)).contains(row.getId()))
                .toList());
        when(core.findRootPage(any(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
            Timestamp cursorTime = invocation.getArgument(2);
            Long cursorId = invocation.getArgument(3);
            int limit = invocation.getArgument(4);
            List<CommentEntity> roots = rows.stream()
                    .filter(row -> row.getParentId() == null)
                    .sorted((a, b) -> {
                        int byActivity = b.getLastActivityAt().compareTo(a.getLastActivityAt());
                        return byActivity != 0 ? byActivity : Long.compare(b.getId(), a.getId());
                    })
                    .filter(row -> cursorTime == null
                            || row.getLastActivityAt().before(cursorTime)
                            || (row.getLastActivityAt().equals(cursorTime) && row.getId() < cursorId))
                    .limit(limit)
                    .toList();
            return new ArrayList<>(roots);
        });
        when(core.replyCounts(anyList())).thenAnswer(invocation -> {
            List<Long> rootIds = invocation.getArgument(0);
            Map<Long, Long> counts = new HashMap<>();
            for (CommentEntity row : rows) {
                if (row.getParentId() != null && rootIds.contains(row.getRootId()) && !row.isDeleted()) {
                    counts.merge(row.getRootId(), 1L, Long::sum);
                }
            }
            return counts;
        });
        when(core.previewReplies(anyList())).thenAnswer(invocation -> {
            List<Long> rootIds = invocation.getArgument(0);
            return rows.stream()
                    .filter(row -> row.getParentId() != null && rootIds.contains(row.getRootId()))
                    .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                    .toList();
        });
        when(core.findAttachments(anyList())).thenReturn(List.of());
        when(core.saveAttachment(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authors.resolveSnapshot(any())).thenReturn(new AuthorSnapshot("Nguyen Van A", null));

        domain = new CommentDomain();
        inject(domain, "coreCommentService", core);
        inject(domain, "attachmentDomain", attachments);
        inject(domain, "authorProfileDomain", authors);
        inject(domain, "maxContentLength", 5000);
        inject(domain, "maxAttachments", 4);
    }

    // ------------------------------------------------------------------ bumping

    @Test
    void rootCreationOwnsItsThreadAndIsNotBumped() {
        CommentView root = domain.create(CommentTargetType.PRODUCT, "42", null, "hello", List.of(), "buyer1");

        CommentEntity row = rows.get(0);
        assertEquals(row.getId(), row.getRootId());
        assertNull(row.getParentId());
        assertEquals("Nguyen Van A", root.getAuthorDisplayName());
        verify(core, never()).bumpRoot(any(), any());
    }

    @Test
    void replyJoinsTheParentsRootAndBumpsIt() {
        Timestamp past = new Timestamp(System.currentTimeMillis() - 60_000);
        CommentEntity rootA = row(1L, null, 1L, past, "root-a");
        CommentEntity rootB = row(2L, null, 2L, past, "root-b");
        // One minute of seeded clock drift would flip the listing order, so the
        // bump must land strictly after both roots' activity.
        Timestamp later = new Timestamp(System.currentTimeMillis() + 60_000);
        CommentEntity parent = row(3L, 1L, 1L, past, "reply-1");
        parent.setCreatedAt(later);

        domain.create(CommentTargetType.PRODUCT, "42", parent.getUuid(), "me too", List.of(), "buyer2");

        ArgumentCaptor<Timestamp> when = ArgumentCaptor.forClass(Timestamp.class);
        verify(core).bumpRoot(eq(rootA.getId()), when.capture());
        assertTrue(when.getValue().after(rootB.getLastActivityAt()),
                "the replied thread must outrank the untouched thread");
        ArgumentCaptor<CommentEntity> savedRows = ArgumentCaptor.forClass(CommentEntity.class);
        verify(core, Mockito.atLeastOnce()).save(savedRows.capture());
        CommentEntity reply = savedRows.getAllValues().get(savedRows.getAllValues().size() - 1);
        assertEquals(1L, reply.getRootId(), "the reply joins the root's thread");
        assertEquals(parent.getId(), reply.getParentId());
    }

    @Test
    void listingRisesWithTheBumpAndPagesByCursor() {
        Timestamp past = new Timestamp(System.currentTimeMillis() - 120_000);
        CommentEntity older = row(1L, null, 1L, past, "older");
        CommentEntity newer = row(2L, null, 2L, past, "newer");
        row(3L, 2L, 2L, past, "child-of-newer");
        // Reply to "older": its tree lifts above "newer" as a whole.
        Timestamp bumped = new Timestamp(System.currentTimeMillis());
        older.setLastActivityAt(bumped);

        ThreadPage page = domain.listRoots(CommentTargetType.PRODUCT, "42", null, 10);

        assertEquals(List.of("older", "newer"),
                page.items().stream().map(CommentView::getContent).toList());
        assertEquals(0L, page.items().get(0).getReplyCount(), "the bumped root has no replies itself");
        assertEquals(1L, page.items().get(1).getReplyCount());
        CommentView preview = page.items().get(1).getPreviewReplies().get(0);
        assertEquals("child-of-newer", preview.getContent());
        assertEquals("Nguyen Van A", preview.getReplyToName(), "the direct parent's author name drives 'replying to @name'");
        assertEquals(newer.getUuid(), preview.getParentUuid());
        assertNull(page.nextCursor(), "a partial page has no next cursor");
    }

    // ------------------------------------------------------------------ writing

    @Test
    void emptyCommentWithoutFileIsRefused() {
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.create(CommentTargetType.PRODUCT, "42", null, "   ", List.of(), "buyer1"));
        assertEquals(400, error.getErrorCode());
    }

    @Test
    void fileOnlyCommentGetsAPlaceholderContent() {
        AttachmentModel model = new AttachmentModel();
        model.setKind(AttachmentKind.IMAGE);
        model.setFileName("photo.png");
        model.setMimeType("image/png");
        model.setSizeBytes(1234L);
        model.setStorageKey("key.png");
        when(attachments.store(any(), any(), any())).thenReturn(model);

        domain.create(CommentTargetType.PRODUCT, "42", null, "",
                List.of(new CommentUploadPayload(new byte[] { 1 }, "photo.png", "image/png")), "buyer1");

        assertEquals("[Image] photo.png", rows.get(0).getContent());
    }

    @Test
    void tooManyAttachmentsIsRefusedBeforeAnythingIsStored() {
        List<CommentUploadPayload> uploads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            uploads.add(new CommentUploadPayload(new byte[] { 1 }, "f" + i + ".png", "image/png"));
        }
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.create(CommentTargetType.PRODUCT, "42", null, "spam", uploads, "buyer1"));
        assertEquals(400, error.getErrorCode());
        verify(attachments, never()).store(any(), any(), any());
    }

    @Test
    void replyToAnotherTargetIsRefused() {
        row(1L, null, 1L, new Timestamp(System.currentTimeMillis()), "other-target");
        CommentEntity foreign = rows.get(0);
        foreign.setTargetType("POST");

        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.create(CommentTargetType.PRODUCT, "42", foreign.getUuid(), "hi",
                        List.of(), "buyer1"));
        assertEquals(400, error.getErrorCode());
    }

    // ------------------------------------------------------------------ identity

    @Test
    void anonymousCallerCannotWrite() {
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.create(CommentTargetType.PRODUCT, "42", null, "hello", List.of(), " "));
        assertEquals(401, error.getErrorCode());
    }

    @Test
    void onlyTheAuthorCanEditOrDelete() {
        CommentEntity own = row(1L, null, 1L, new Timestamp(System.currentTimeMillis()), "my words");

        assertEquals(403, assertThrows(ApplicationException.class,
                () -> domain.edit(own.getUuid(), "hijacked", "intruder")).getErrorCode());
        assertEquals(403, assertThrows(ApplicationException.class,
                () -> domain.delete(own.getUuid(), "intruder")).getErrorCode());
        assertTrue(rows.get(0).getContent().equals("my words"), "a refused edit changed nothing");
    }

    @Test
    void authorEditMarksTheCommentAndSoftDeleteKeepsTheTree() {
        CommentEntity own = row(1L, null, 1L, new Timestamp(System.currentTimeMillis()), "my words");
        row(2L, 1L, 1L, new Timestamp(System.currentTimeMillis()), "a reply");

        domain.delete(own.getUuid(), "buyer1");

        assertTrue(own.isDeleted());
        assertEquals("", own.getContent());
        assertEquals(2L, rows.size(), "the reply's parent row still exists (placeholder rendering)");
    }

    @Test
    void moderatorDeleteNeedsNoAuthorship() {
        CommentEntity foreign = row(1L, null, 1L, new Timestamp(System.currentTimeMillis()), "spam");
        foreign.setAuthorUsername("spammer");

        domain.deleteAsModerator(foreign.getUuid());

        assertTrue(foreign.isDeleted());
    }

    // --------------------------------------------------------------------- misc

    @Test
    void invalidCursorIsA400() {
        ApplicationException error = assertThrows(ApplicationException.class,
                () -> domain.listRoots(CommentTargetType.PRODUCT, "42", "not-a-cursor", 10));
        assertEquals(400, error.getErrorCode());
    }

    // ------------------------------------------------------------------ fixtures

    private CommentEntity row(Long id, Long parentId, Long rootId, Timestamp activity, String content) {
        CommentEntity entity = new CommentEntity();
        entity.setId(id);
        entity.setUuid("uuid-" + content);
        entity.setTargetType("PRODUCT");
        entity.setTargetId("42");
        entity.setParentId(parentId);
        entity.setRootId(rootId);
        entity.setAuthorUsername("buyer1");
        entity.setAuthorDisplayName("Nguyen Van A");
        entity.setContent(content);
        entity.setDeleted(false);
        entity.setCreatedAt(activity);
        entity.setLastActivityAt(activity);
        rows.add(entity);
        return entity;
    }

    private static void inject(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
