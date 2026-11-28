package com.qrpublic.apartment.comment.core.service;

import com.qrpublic.apartment.comment.entity.CommentAuthorAvatarEntity;
import com.qrpublic.apartment.comment.repository.CommentAuthorAvatarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Transactional data access of the author cache row (chat-app's core layer). */
@Service
public class CoreAuthorService {

    @Autowired
    private CommentAuthorAvatarRepository authorAvatarRepository;

    @Transactional(readOnly = true)
    public Optional<CommentAuthorAvatarEntity> findAuthor(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return authorAvatarRepository.findById(username);
    }

    @Transactional
    public CommentAuthorAvatarEntity saveAuthor(CommentAuthorAvatarEntity entity) {
        return authorAvatarRepository.save(entity);
    }
}
