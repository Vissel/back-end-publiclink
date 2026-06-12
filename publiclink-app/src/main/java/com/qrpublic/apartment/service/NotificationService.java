package com.qrpublic.apartment.service;

import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.*;
import com.qrpublic.apartment.model.notification.NotificationCountResponse;
import com.qrpublic.apartment.model.notification.NotificationDTO;
import com.qrpublic.apartment.model.notification.SellerEnvironmentListResponse;
import com.qrpublic.apartment.model.notification.SellerEnvironmentListResponse.EnvironmentItem;
import com.qrpublic.apartment.model.notification.SellerEnvironmentListResponse.SellerEnvironments;
import com.qrpublic.apartment.model.notification.SellerListResponse;
import com.qrpublic.apartment.model.notification.SellerListResponse.SellerItem;
import com.qrpublic.apartment.repository.*;
import com.qrpublic.apartment.requestmodel.SendNotificationRequest;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.ProcessCallback;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository recipientRepository;

    @Autowired
    private SaleEnvironmentRepository saleEnvironmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private PublicLinkServiceTemplate publicLinkServiceTemplate;

    /**
     * Admin sends notification to selected sellers/environments or all sellers.
     */
    public Result<Void> sendNotification(SendNotificationRequest request, String adminUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<SendNotificationRequest, Void>() {
            @Override
            public SendNotificationRequest getRequest() {
                return request;
            }

            @Override
            public void preProcess(SendNotificationRequest req) {
                Assert.hasText(req.getTitle(), "Notification title is required.");
                Assert.hasText(req.getMessage(), "Notification message is required.");
                if (!req.isSelectAll()) {
                    Assert.notEmpty(req.getTargets(), "At least one target is required when not selecting all.");
                }
            }

            @Override
            public Void process() {
                Notification notification = new Notification();
                notification.setTitle(request.getTitle());
                notification.setMessage(request.getMessage());
                notification.setCreatedBy(adminUsername);

                // Determine target type
                boolean hasSpecificEnvs = false;
                if (!request.isSelectAll() && request.getTargets() != null) {
                    hasSpecificEnvs = request.getTargets().stream()
                            .anyMatch(t -> t.getRequestUuids() != null && !t.getRequestUuids().isEmpty());
                }
                notification.setTargetType(hasSpecificEnvs ? "ENVIRONMENT" : "SELLER");

                // Build recipient list
                List<NotificationRecipient> recipients = new ArrayList<>();

                if (request.isSelectAll()) {
                    List<Request> allRequests = requestRepository.findAll();
                    Set<String> sellerUsernames = allRequests.stream()
                            .map(Request::getSellerName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    for (String sellerUsername : sellerUsernames) {
                        NotificationRecipient recipient = new NotificationRecipient();
                        recipient.setNotification(notification);
                        recipient.setSellerUsername(sellerUsername);
                        recipient.setRead(false);
                        recipients.add(recipient);
                    }
                } else {
                    for (SendNotificationRequest.NotificationTarget target : request.getTargets()) {
                        if (target.getRequestUuids() != null && !target.getRequestUuids().isEmpty()) {
                            for (String reqUuid : target.getRequestUuids()) {
                                NotificationRecipient recipient = new NotificationRecipient();
                                recipient.setNotification(notification);
                                recipient.setSellerUsername(target.getSellerUsername());
                                recipient.setRequestUuid(reqUuid);
                                recipient.setRead(false);
                                recipients.add(recipient);
                            }
                        } else {
                            NotificationRecipient recipient = new NotificationRecipient();
                            recipient.setNotification(notification);
                            recipient.setSellerUsername(target.getSellerUsername());
                            recipient.setRead(false);
                            recipients.add(recipient);
                        }
                    }
                }

                notification.setRecipients(recipients);
                notificationRepository.save(notification);
                log.info("Notification [{}] sent by admin [{}] to {} recipients",
                        notification.getId(), adminUsername, recipients.size());
                return null;
            }
        });
    }

    /**
     * Get unread notification count for a seller.
     */
    public Result<NotificationCountResponse> getUnreadCount(String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, NotificationCountResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
            }

            @Override
            public NotificationCountResponse process() {
                long count = recipientRepository.countBySellerUsernameAndIsRead(sellerUsername, false);
                return new NotificationCountResponse(count);
            }
        });
    }

    /**
     * Get paginated notifications for a seller.
     */
    public Result<List<NotificationDTO>> getNotifications(String sellerUsername, int page, int size) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, List<NotificationDTO>>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
            }

            @Override
            public List<NotificationDTO> process() {
                PageRequest pageRequest = PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "notification.createdAt"));
                Page<NotificationRecipient> recipientPage = recipientRepository.findBySellerUsername(sellerUsername,
                        pageRequest);

                // Batch lookup sender info
                List<String> senderUsernames = recipientPage.getContent().stream()
                        .map(r -> r.getNotification().getCreatedBy())
                        .distinct()
                        .collect(Collectors.toList());
                Map<String, User> senderMap = new HashMap<>();
                if (!senderUsernames.isEmpty()) {
                    userRepository.findByUserNames(senderUsernames)
                            .forEach(u -> senderMap.put(u.getUserName(), u));
                }

                return recipientPage.getContent().stream()
                        .map(r -> {
                            User sender = senderMap.get(r.getNotification().getCreatedBy());
                            return toDTO(r,
                                    sender != null ? sender.getName() : r.getNotification().getCreatedBy(),
                                    sender != null ? sender.getLink() : "");
                        })
                        .collect(Collectors.toList());
            }
        });
    }

    /**
     * Get single notification detail and mark as read.
     */
    public Result<NotificationDTO> getNotificationDetail(String sellerUsername, Long notificationId) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, NotificationDTO>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
                Assert.notNull(notificationId, "Notification ID is required.");
            }

            @Override
            public NotificationDTO process() {
                NotificationRecipient recipient = recipientRepository
                        .findBySellerUsernameAndNotificationId(sellerUsername, notificationId)
                        .orElseThrow(() -> new RuntimeException("Notification not found for this seller."));

                // Mark as read if not already
                if (!recipient.isRead()) {
                    recipient.setRead(true);
                    recipient.setReadAt(Timestamp.from(Instant.now()));
                    recipientRepository.save(recipient);
                }

                // Lookup sender info
                String createdBy = recipient.getNotification().getCreatedBy();
                List<User> senders = userRepository.findByUserNames(List.of(createdBy));
                User sender = senders.isEmpty() ? null : senders.get(0);
                return toDTO(recipient,
                        sender != null ? sender.getName() : createdBy,
                        sender != null ? sender.getLink() : "");
            }
        });
    }

    /**
     * Mark a single notification as read.
     */
    public Result<Void> markAsRead(String sellerUsername, Long notificationId) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
                Assert.notNull(notificationId, "Notification ID is required.");
            }

            @Override
            public Void process() {
                recipientRepository.findBySellerUsernameAndNotificationId(sellerUsername, notificationId)
                        .ifPresent(recipient -> {
                            if (!recipient.isRead()) {
                                recipient.setRead(true);
                                recipient.setReadAt(Timestamp.from(Instant.now()));
                                recipientRepository.save(recipient);
                            }
                        });
                return null;
            }
        });
    }

    /**
     * Mark all notifications as read for a seller.
     */
    public Result<Void> markAllAsRead(String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, Void>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
            }

            @Override
            public Void process() {
                recipientRepository.markAllAsRead(sellerUsername, Timestamp.from(Instant.now()));
                return null;
            }
        });
    }

    /**
     * Get paginated list of sellers with environment counts (for admin compose UI).
     */
    public Result<SellerListResponse> getSellers(String search, int page, int size) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, SellerListResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                // no validation needed
            }

            @Override
            public SellerListResponse process() {
                PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "userName"));
                Page<User> sellerPage = userRepository.findSellers(
                        (search == null || search.isBlank()) ? null : search.trim(),
                        pageRequest);

                List<User> sellers = sellerPage.getContent();
                List<String> usernames = sellers.stream()
                        .map(User::getUserName)
                        .collect(Collectors.toList());

                // Get environment counts for these sellers
                Map<String, Long> envCountMap = new HashMap<>();
                if (!usernames.isEmpty()) {
                    List<Object[]> counts = saleEnvironmentRepository.countEnvironmentsBySellerUsernames(usernames);
                    for (Object[] row : counts) {
                        envCountMap.put((String) row[0], (Long) row[1]);
                    }
                }

                List<SellerItem> items = sellers.stream()
                        .map(u -> new SellerItem(
                                u.getUserName(),
                                u.getName(),
                                envCountMap.getOrDefault(u.getUserName(), 0L)))
                        .collect(Collectors.toList());

                return new SellerListResponse(items, sellerPage.getTotalElements());
            }
        });
    }

    /**
     * Get environments for a specific seller (on-demand loading).
     */
    @Transactional(readOnly = true)
    public Result<SellerEnvironmentListResponse> getSellerEnvironments(String sellerUsername) {
        return publicLinkServiceTemplate.execute(new ProcessCallback<Void, SellerEnvironmentListResponse>() {
            @Override
            public Void getRequest() {
                return null;
            }

            @Override
            public void preProcess(Void request) {
                Assert.hasText(sellerUsername, "Seller username is required.");
            }

            @Override
            public SellerEnvironmentListResponse process() {
                List<SaleEnvironment> envs = saleEnvironmentRepository.findBySellerUsername(sellerUsername);

                List<EnvironmentItem> envItems = envs.stream()
                        .map(se -> {
                            String productName = se.getRequest().getProducts() != null
                                    && !se.getRequest().getProducts().isEmpty()
                                    ? se.getRequest().getProducts().get(0).getProductName()
                                    : CommonConstant.EMPTY;
                            return new EnvironmentItem(
                                    se.getRequest().getReqUUID(),
                                    productName,
                                    se.getCreatedAt() != null ? se.getCreatedAt().toString() : "",
                                    se.isState(),
                                    se.getEndedAt() != null ? se.getEndedAt().toString() : "");
                        })
                        .collect(Collectors.toList());

                SellerEnvironments sellerEnv = new SellerEnvironments(sellerUsername, sellerUsername, envItems);
                return new SellerEnvironmentListResponse(List.of(sellerEnv));
            }
        });
    }

    private static NotificationDTO toDTO(NotificationRecipient recipient, String senderName, String senderEmail) {
        Notification n = recipient.getNotification();
        return new NotificationDTO(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getTargetType(),
                recipient.getRequestUuid(),
                recipient.isRead(),
                n.getCreatedAt() != null ? n.getCreatedAt().toString() : "",
                senderName,
                senderEmail);
    }
}
