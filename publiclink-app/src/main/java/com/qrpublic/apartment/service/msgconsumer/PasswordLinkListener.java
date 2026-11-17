package com.qrpublic.apartment.service.msgconsumer;

import com.qrpublic.apartment.adapter.msgbroker.model.PasswordLinkEvent;
import com.qrpublic.apartment.app_config.PasswordLinkRabbitConfig;
import com.qrpublic.apartment.core.service.CoreNotificationService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Consumes password setup/reset link events: emails the link when the buyer
 * has an address on file and always records an in-app notification carrying
 * the link so the seller/admin can forward it manually. Manual ack with the
 * project's acknowledge-mode=manual; failures reject without requeue (the
 * link remains copyable in the UI, so a lost email is not data loss).
 */
@Slf4j
@Component
public class PasswordLinkListener {

    /** bounded idempotency memory: redelivered messageIds are acked silently */
    private static final int DEDUP_MAX = 512;

    private final Set<String> processedIds = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>(DEDUP_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_MAX;
                }
            }));

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private CoreNotificationService coreNotificationService;

    @Value("${app.public.url:https://share-link.top}")
    private String appPublicUrl;

    @Value("${MAIL_USERNAME:}")
    private String mailFrom;

    @RabbitListener(queues = PasswordLinkRabbitConfig.QUEUE)
    public void handlePasswordLink(PasswordLinkEvent event, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            if (event == null || event.getUsername() == null || event.getUsername().isBlank()) {
                channel.basicAck(tag, false);
                return;
            }
            if (event.getMessageId() != null && !processedIds.add(event.getMessageId())) {
                log.info("Password link event [{}] already processed, acking redelivery", event.getMessageId());
                channel.basicAck(tag, false);
                return;
            }

            deliver(event);

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process password link event: {}", event, e);
            channel.basicReject(tag, false);
        }
    }

    private void deliver(PasswordLinkEvent event) {
        String absoluteLink = event.getSetupLink() != null && event.getSetupLink().startsWith("http")
                ? event.getSetupLink()
                : appPublicUrl + (event.getSetupLink() == null ? "" : event.getSetupLink());

        boolean reset = "RESET".equalsIgnoreCase(event.getPurpose());
        String subject = reset
                ? "[PublicLink] Password reset link for " + event.getUsername()
                : "[PublicLink] Set your password to book yoga classes";

        if (event.getEmail() != null && !event.getEmail().isBlank()) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                if (mailFrom != null && !mailFrom.isBlank()) {
                    msg.setFrom(mailFrom);
                }
                msg.setTo(event.getEmail());
                msg.setSubject(subject);
                msg.setText(("Yêu cầu đặt lại mật khẩu / Set up your account:\n\n"
                        + "Username: " + event.getUsername() + "\n\n"
                        + (reset
                                ? "Your password was reset by an administrator. Choose a new password here:\n"
                                : "A seller gave you a yoga payment plan. Choose your password here:\n")
                        + absoluteLink + "\n\n"
                        + "The link works once and expires within 48 hours.\n"));
                mailSender.send(msg);
                log.info("Password link emailed to [{}] for user [{}]", event.getEmail(), event.getUsername());
            } catch (Exception e) {
                log.error("Password link email to [{}] failed for user [{}]: {}",
                        event.getEmail(), event.getUsername(), e.getMessage());
            }
        } else {
            log.info("No email on password link event for [{}], relying on in-app copy link",
                    event.getUsername());
        }

        // In-app notification so the link is always copyable by a human:
        // SETUP goes to the seller who created the plan, RESET to the admin
        // who triggered it (recipients are plain usernames).
        String recipient = reset ? event.getRequestedBy() : event.getSellerUsername();
        if (recipient != null && !recipient.isBlank()) {
            coreNotificationService.notifyUsernames(
                    Collections.singletonList(recipient),
                    reset ? "Password reset link issued" : "Buyer password setup link ready",
                    "Account: " + event.getUsername() + "\n" + absoluteLink,
                    reset ? "PASSWORD_RESET" : "BUYER_SETUP",
                    recipient);
        }
    }
}
