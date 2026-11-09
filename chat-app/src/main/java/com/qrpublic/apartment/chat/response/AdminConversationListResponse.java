package com.qrpublic.apartment.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Administrator inbox page: every conversation with its counters, plus how many
 * visitors and consoles are currently connected — the concurrency picture the
 * bridge has to hold up under.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminConversationListResponse {

    private List<ChatConversationResponse> conversations;

    private long total;

    private int page;

    private int size;

    /** Visitors with a live event stream right now. */
    private int onlineClients;

    /** Open administrator console streams. */
    private int onlineAdmins;

    /** True while the Zalo leg is switched on. */
    private boolean zaloEnabled;
}
