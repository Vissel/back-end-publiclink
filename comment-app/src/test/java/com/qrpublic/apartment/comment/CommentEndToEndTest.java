package com.qrpublic.apartment.comment;

import com.qrpublic.apartment.comment.core.model.CommentTargetType;
import com.qrpublic.apartment.comment.core.model.CommentView;
import com.qrpublic.apartment.comment.domain.AuthorProfileDomain;
import com.qrpublic.apartment.comment.domain.AuthorSnapshot;
import com.qrpublic.apartment.comment.domain.CommentDomain;
import com.qrpublic.apartment.comment.domain.ThreadPage;
import com.qrpublic.apartment.comment.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end wiring through the REAL stack: Liquibase runs the production
 * changelog against H2 in MySQL mode, Hibernate validates every entity mapping
 * against the created tables, and the tree/bump rules execute through actual
 * SQL. Only the external profile services are mocked (no user/publiclink host
 * exists in the test environment).
 */
@SpringBootTest
@ActiveProfiles("boottest")
@Transactional
class CommentEndToEndTest {

    @Autowired
    CommentDomain commentDomain;

    @MockBean
    AuthorProfileDomain authorProfileDomain;

    @Test
    void newestFirstAndAReplyLiftsTheWholeTree() {
        stubNames();

        // Two threads on the SAME product target: the listing is per target.
        CommentView older = commentDomain.create(CommentTargetType.PRODUCT, "7",
                null, "older thread", List.of(), "buyerA");
        commentDomain.create(CommentTargetType.PRODUCT, "7", null, "newer thread", List.of(), "buyerB");

        List<String> names = contentOrder();
        assertEquals(List.of("newer thread", "older thread"), names, "newest root displays higher");

        // A sub-reply two levels deep inside "older" lifts that whole tree.
        CommentView child = commentDomain.create(CommentTargetType.PRODUCT, "7",
                older.getUuid(), "reply to older", List.of(), "buyerC");
        commentDomain.create(CommentTargetType.PRODUCT, "7",
                child.getUuid(), "reply to the reply", List.of(), "buyerD");

        assertEquals(List.of("older thread", "newer thread"), contentOrder(),
                "the replied tree rises as one unit, sub-replies included");

        // The lifted root reports its live counts and previews.
        ThreadPage page = commentDomain.listRoots(CommentTargetType.PRODUCT, "7", null, 10);
        CommentView listed = page.items().get(0);
        assertEquals(older.getUuid(), listed.getUuid());
        assertEquals(2L, listed.getReplyCount());
        assertEquals(2, listed.getPreviewReplies().size());
        assertEquals("Name buyerA", listed.getPreviewReplies().get(0).getReplyToName());

        // Thread pagination walks replies chronologically under the root.
        List<CommentView> replies = commentDomain.listReplies(older.getUuid(), null, 50);
        assertEquals(2, replies.size());
        assertEquals(child.getUuid(), replies.get(0).getUuid());
        assertEquals(child.getUuid(), replies.get(1).getParentUuid());
    }

    @Test
    void threadsStaySeparatedPerTarget() {
        stubNames();
        commentDomain.create(CommentTargetType.PRODUCT, "8", null, "product eight", List.of(), "buyerA");
        commentDomain.create(CommentTargetType.POST, "8", null, "post eight", List.of(), "buyerA");

        assertEquals(1, commentDomain.listRoots(CommentTargetType.PRODUCT, "8", null, 10).items().size());
        assertEquals("post eight",
                commentDomain.listRoots(CommentTargetType.POST, "8", null, 10).items().get(0).getContent());
    }

    @Test
    void authorOnlyRulesHoldOnRealRows() {
        stubNames();

        CommentView own = commentDomain.create(CommentTargetType.GUIDELINE, "guide-1",
                null, "my words", List.of(), "writer1");

        assertEquals(403, assertThrows(ApplicationException.class,
                () -> commentDomain.edit(own.getUuid(), "hijack", "intruder")).getErrorCode());
        assertEquals(401, assertThrows(ApplicationException.class,
                () -> commentDomain.delete(own.getUuid(), " ")).getErrorCode());

        CommentView edited = commentDomain.edit(own.getUuid(), "fixed words", "writer1");
        assertEquals("fixed words", edited.getContent());
        assertTrue(edited.getEditedAt() != null);

        commentDomain.delete(own.getUuid(), "writer1");
        assertTrue(commentDomain.listRoots(CommentTargetType.GUIDELINE, "guide-1", null, 10)
                .items().get(0).isDeleted(), "the soft-deleted root stays visible as a placeholder");

        commentDomain.deleteAsModerator(own.getUuid());
        assertTrue(commentDomain.listRoots(CommentTargetType.GUIDELINE, "guide-1", null, 10)
                .items().get(0).isDeleted());
    }

    private void stubNames() {
        when(authorProfileDomain.resolveSnapshot(any()))
                .thenAnswer(invocation -> new AuthorSnapshot("Name " + invocation.getArgument(0), null));
    }

    private List<String> contentOrder() {
        return commentDomain.listRoots(CommentTargetType.PRODUCT, "7", null, 10)
                .items().stream().map(CommentView::getContent).toList();
    }
}
