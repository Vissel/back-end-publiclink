package com.qrpublic.apartment.comment.repository;

import com.qrpublic.apartment.comment.entity.CommentAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentAttachmentRepository extends JpaRepository<CommentAttachmentEntity, Long> {

    Optional<CommentAttachmentEntity> findByUuid(String uuid);

    List<CommentAttachmentEntity> findByCommentIdOrderByIdAsc(Long commentId);

    List<CommentAttachmentEntity> findByCommentIdInOrderByIdAsc(List<Long> commentIds);
}
