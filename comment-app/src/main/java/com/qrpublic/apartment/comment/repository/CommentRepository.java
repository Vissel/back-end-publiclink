package com.qrpublic.apartment.comment.repository;

import com.qrpublic.apartment.comment.entity.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    Optional<CommentEntity> findByUuid(String uuid);

    /**
     * Root listing of one target, newest activity first (a reply bumps its
     * root's last_activity_at, lifting the whole tree). Keyset paging on
     * (lastActivityAt, id) so a page never splits on concurrent inserts.
     */
    @Query("SELECT c FROM CommentEntity c WHERE c.targetType = :targetType AND c.targetId = :targetId "
            + "AND c.parentId IS NULL "
            + "AND (c.lastActivityAt < :cursorTime OR (c.lastActivityAt = :cursorTime AND c.id < :cursorId)) "
            + "ORDER BY c.lastActivityAt DESC, c.id DESC")
    List<CommentEntity> findRootPage(@Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("cursorTime") Timestamp cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("SELECT c FROM CommentEntity c WHERE c.targetType = :targetType AND c.targetId = :targetId "
            + "AND c.parentId IS NULL ORDER BY c.lastActivityAt DESC, c.id DESC")
    List<CommentEntity> findFirstRootPage(@Param("targetType") String targetType,
            @Param("targetId") String targetId,
            Pageable pageable);

    /** Whole thread under one root, chronological (oldest first, chat-style).
     * The root row itself is excluded (its rootId equals its own id). */
    @Query("SELECT c FROM CommentEntity c WHERE c.rootId = :rootId AND c.id <> c.rootId "
            + "AND c.id > :afterId ORDER BY c.id ASC")
    List<CommentEntity> findThreadReplies(@Param("rootId") Long rootId,
            @Param("afterId") Long afterId,
            Pageable pageable);

    /** Reply counts for a page of roots in one grouped query. */
    @Query("SELECT c.rootId, COUNT(c) FROM CommentEntity c WHERE c.rootId IN :rootIds "
            + "AND c.id <> c.rootId AND c.deleted = false GROUP BY c.rootId")
    List<Object[]> countRepliesForRoots(@Param("rootIds") List<Long> rootIds);

    /** First replies of a page of roots (thread preview under each root). */
    @Query("SELECT c FROM CommentEntity c WHERE c.rootId IN :rootIds AND c.id <> c.rootId "
            + "ORDER BY c.rootId ASC, c.id ASC")
    List<CommentEntity> findPreviewReplies(@Param("rootIds") List<Long> rootIds);

    /** Live comment count per target id, one grouped query (internal counts). */
    @Query("SELECT c.targetId, COUNT(c) FROM CommentEntity c WHERE c.targetType = :targetType "
            + "AND c.targetId IN :targetIds AND c.deleted = false GROUP BY c.targetId")
    List<Object[]> countLiveByTarget(@Param("targetType") String targetType,
            @Param("targetIds") List<String> targetIds);

    /**
     * The bump: a reply lifts its entire tree by refreshing the root's
     * activity key. Root rows are always updated through this single statement.
     * flushAutomatically matters: the bulk update bypasses the persistence
     * context, so the reply's own pending insert must reach the DB first.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CommentEntity c SET c.lastActivityAt = :when WHERE c.id = :rootId")
    int bumpRoot(@Param("rootId") Long rootId, @Param("when") Timestamp when);
}
