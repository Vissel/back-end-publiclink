package com.qrpublic.apartment.chat.controller;

import com.qrpublic.apartment.chat.core.model.ChatAttachmentKind;
import com.qrpublic.apartment.chat.core.model.ChatAttachmentModel;
import com.qrpublic.apartment.chat.domain.ChatAttachmentDownload;
import com.qrpublic.apartment.chat.exception.ApplicationException;
import com.qrpublic.apartment.chat.request.ChatUploadPayload;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared plumbing of the four attachment endpoints: reading one multipart
 * upload with a hard byte cap, and turning stored bytes into a download
 * response (inline for images, attachment for documents).
 */
final class ChatAttachmentHttp {

    private ChatAttachmentHttp() {
    }

    /**
     * Collects one uploaded file into memory, refusing anything above the cap
     * before it can fill the heap or the disk. The per-kind limit and the
     * content checks run later, in the attachment domain.
     */
    static Mono<ChatUploadPayload> readCapped(FilePart file, int maxBytes) {
        MediaType contentType = file.headers().getContentType();
        return file.content()
                .reduce(new ByteArrayOutputStream(), (out, buffer) -> {
                    int readable = buffer.readableByteCount();
                    if (out.size() + readable > maxBytes) {
                        DataBufferUtils.release(buffer);
                        throw new ApplicationException("The file is larger than the allowed limit.", 413);
                    }
                    byte[] chunk = new byte[readable];
                    buffer.read(chunk);
                    DataBufferUtils.release(buffer);
                    out.write(chunk, 0, readable);
                    return out;
                })
                .map(out -> new ChatUploadPayload(out.toByteArray(), file.filename(),
                        contentType == null ? null : contentType.toString()));
    }

    static ResponseEntity<byte[]> downloadResponse(ChatAttachmentDownload download) {
        ChatAttachmentModel attachment = download.attachment();
        boolean inline = attachment.getKind() == ChatAttachmentKind.IMAGE;
        String encodedName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(attachment.getMimeType()));
        headers.setContentLength(download.bytes().length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename=\"" + encodedName + "\"; filename*=UTF-8''"
                        + encodedName);
        // Immutable content: the uuid in the URL never changes meaning.
        headers.setCacheControl("private, max-age=31536000, immutable");
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    static <T> ResponseEntity<T> error(ApplicationException error) {
        return ResponseEntity.status(error.getErrorCode()).<T>build();
    }
}
