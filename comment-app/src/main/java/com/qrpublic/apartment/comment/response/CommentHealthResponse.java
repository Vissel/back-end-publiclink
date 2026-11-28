package com.qrpublic.apartment.comment.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Liveness payload of the comment service. Deliberately minimal — this endpoint
 * is public (it sits under /api/v1/public), so no counts or identifiers appear.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentHealthResponse {

    /** Always "UP" once the context is serving requests. */
    private String status;

    private String service;

    /** Public namespace this service answers under, for routing diagnostics. */
    private String namespace;
}
