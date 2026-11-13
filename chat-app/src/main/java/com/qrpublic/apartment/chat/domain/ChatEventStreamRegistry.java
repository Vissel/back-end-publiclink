package com.qrpublic.apartment.chat.domain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fan-out of chat events to open server-sent-event streams.
 *
 * One logical stream per CONVERSATION, one shared stream for the administrator
 * console. The key used to be the device id, which is shared by every account
 * that signs in on one browser - two users therefore joined the same sink and
 * read each other's messages. A conversation belongs to exactly one owner (or
 * to one anonymous device), so it is the only safe fan-out key.
 *
 * Entries are dropped when their last subscriber disappears, so a visitor who
 * closes the tab stops holding a buffer - the reason the visitor is treated as
 * "lost" until the next reconnect.
 *
 * This is deliberately not a durable channel: everything a client missed while
 * offline lives in the database and is replayed by id on reconnect.
 */
@Slf4j
@Component
public class ChatEventStreamRegistry {

    private static final int BUFFER_SIZE = 256;

    private final Map<String, StreamHolder> clientStreams = new ConcurrentHashMap<>();
    private final StreamHolder adminStream = new StreamHolder();

    /** Opens (or joins) the event stream of one conversation. */
    public Flux<ServerSentEvent<String>> clientStream(String conversationUuid) {
        StreamHolder holder = clientStreams.compute(conversationUuid, (key, existing) -> {
            StreamHolder target = existing == null ? new StreamHolder() : existing;
            target.subscribers.incrementAndGet();
            return target;
        });
        return holder.sink.asFlux()
                .doFinally(signal -> releaseClient(conversationUuid));
    }

    /** Opens (or joins) the shared administrator stream. */
    public Flux<ServerSentEvent<String>> adminStream() {
        adminStream.subscribers.incrementAndGet();
        return adminStream.sink.asFlux()
                .doFinally(signal -> {
                    int remaining = adminStream.subscribers.decrementAndGet();
                    if (remaining <= 0) {
                        adminStream.subscribers.set(0);
                    }
                });
    }

    public void publishToClient(String conversationUuid, ServerSentEvent<String> event) {
        StreamHolder holder = clientStreams.get(conversationUuid);
        if (holder != null) {
            holder.emit(event);
        }
    }

    public void publishToAdmins(ServerSentEvent<String> event) {
        adminStream.emit(event);
    }

    /** True while the conversation has a live stream — used to skip redundant pushes. */
    public boolean isClientOnline(String conversationUuid) {
        StreamHolder holder = clientStreams.get(conversationUuid);
        return holder != null && holder.subscribers.get() > 0;
    }

    public int onlineClientCount() {
        return (int) clientStreams.values().stream().filter(h -> h.subscribers.get() > 0).count();
    }

    public int onlineAdminCount() {
        return adminStream.subscribers.get();
    }

    private void releaseClient(String conversationUuid) {
        clientStreams.computeIfPresent(conversationUuid,
                (key, holder) -> holder.subscribers.decrementAndGet() <= 0 ? null : holder);
    }

    /**
     * A sink plus its subscriber count. Emission is synchronised because a sink
     * only tolerates one producer at a time, while status events can arrive from
     * webhook threads, scheduler threads and request threads simultaneously.
     */
    private static final class StreamHolder {
        private final Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast()
                .onBackpressureBuffer(BUFFER_SIZE, false);
        private final AtomicInteger subscribers = new AtomicInteger(0);

        void emit(ServerSentEvent<String> event) {
            synchronized (this) {
                Sinks.EmitResult result = sink.tryEmitNext(event);
                if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                    log.warn("Chat stream emit failed for one subscriber: {}", result);
                }
            }
        }
    }
}
