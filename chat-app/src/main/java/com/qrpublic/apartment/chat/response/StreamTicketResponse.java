package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single-use credential for the administrator event stream.
 *
 * EventSource cannot attach an Authorization header, so the console asks for a
 * short-lived ticket over an ordinary JWT-guarded call and puts the ticket in
 * the stream URL. The ticket is consumed on first use and expires on its own,
 * which keeps the long-lived URL out of server logs and out of other people's
 * hands.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamTicketResponse {

    private String ticket;

    /** Seconds the ticket stays valid, and how long a reconnect may reuse it. */
    private long expiresInSeconds;
}
