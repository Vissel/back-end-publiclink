package com.qrpublic.apartment.comment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Comment service: threaded comments with image/file attachments on generic
 * targets (PRODUCT today, POST and GUIDELINE when those features land).
 *
 * Its own deployable on purpose, mirroring chat-app: the schema
 * (comment_schema), the Liquibase changelog, the upload directory and the
 * gateway namespace (/comment) all belong to this module alone, so releasing
 * comments never touches publiclink-app, the sole DDL owner of publiclink-db.
 */
@SpringBootApplication
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }
}
