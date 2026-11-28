package com.qrpublic.apartment.comment.repository;

import com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentAuthorAvatarRepository extends JpaRepository<CommentAuthorAvatarEntity, String> {
}
