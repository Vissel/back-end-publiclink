package com.qrpublic.apartment.comment.convertor;

import com.qrpublic.apartment.comment.core.model.CommentAttachmentView;
import com.qrpublic.apartment.comment.core.model.CommentView;
import com.qrpublic.apartment.comment.response.CommentAttachmentDTO;
import com.qrpublic.apartment.comment.response.CommentDTO;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

/**
 * View-to-DTO mapping. The internal row id never reaches the wire; URLs are
 * relative to this service's gateway namespace (/comment) so the frontend only
 * prepends the gateway base.
 */
public final class CommentConvertor {

    private CommentConvertor() {
    }

    public static CommentDTO toDTO(CommentView view) {
        if (view == null) {
            return null;
        }
        CommentDTO dto = new CommentDTO();
        dto.setUuid(view.getUuid());
        dto.setParentUuid(view.getParentUuid());
        dto.setReplyToName(view.getReplyToName());
        dto.setTargetType(view.getTargetType());
        dto.setTargetId(view.getTargetId());
        dto.setAuthorUsername(view.getAuthorUsername());
        dto.setAuthorDisplayName(view.getAuthorDisplayName());
        dto.setAvatarUrl(avatarUrl(view.getAuthorUsername()));
        dto.setContent(view.getContent());
        dto.setDeleted(view.isDeleted());
        dto.setCreatedAt(toIso(view.getCreatedAt()));
        dto.setEditedAt(toIso(view.getEditedAt()));
        dto.setReplyCount(view.getReplyCount());
        List<CommentView> previews = view.getPreviewReplies();
        dto.setPreviewReplies(previews == null ? List.of() : previews.stream()
                .map(CommentConvertor::toDTO)
                .toList());
        List<CommentAttachmentView> attachments = view.getAttachments();
        dto.setAttachments(attachments == null ? List.of() : attachments.stream()
                .map(CommentConvertor::toDTO)
                .toList());
        return dto;
    }

    public static List<CommentDTO> toDTOs(List<CommentView> views) {
        return views.stream().map(CommentConvertor::toDTO).toList();
    }

    private static CommentAttachmentDTO toDTO(CommentAttachmentView view) {
        CommentAttachmentDTO dto = new CommentAttachmentDTO();
        dto.setUuid(view.getUuid());
        dto.setKind(view.getKind() == null ? null : view.getKind().name());
        dto.setFileName(view.getFileName());
        dto.setMimeType(view.getMimeType());
        dto.setSizeBytes(view.getSizeBytes());
        if (view.getUuid() != null) {
            dto.setDownloadUrl("/comment/api/v1/public/attachment/" + view.getUuid());
        }
        return dto;
    }

    private static String avatarUrl(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return "/comment/api/v1/public/avatar/"
                + URLEncoder.encode(username, StandardCharsets.UTF_8);
    }

    private static String toIso(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
