package com.qrpublic.apartment.domain.qr;

import com.qrpublic.apartment.core.service.CorePaymentService;
import com.qrpublic.apartment.core.service.CoreTransferInfoService;
import com.qrpublic.apartment.domain.paymentplan.event.PaymentCreatedEvent;
import com.qrpublic.apartment.entity.TransferInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Builds the VietQR "Quick Link" payment QR for a freshly created payment and
 * stores it against the payment code, so the seller payment page can render it.
 *
 * Quick Link form (no API account needed):
 *   https://img.vietqr.io/image/{BANK_ID}-{ACCOUNT_NO}-{TEMPLATE}.png
 *       ?amount={SO_TIEN}&addInfo={NOI_DUNG}&accountName={TEN_CHU_TK}
 *
 * The receiving account is the admin's default transfer_info row (the one set
 * in admin settings). Runs as a listener on PaymentCreatedEvent, i.e. after the
 * assignment + payment transaction has committed.
 */
@Slf4j
@Component
public class GenerateQRDomain {

    private static final String QUICK_LINK_BASE = "https://img.vietqr.io/image/";
    private static final String TEMPLATE = "compact2";
    private static final int ADD_INFO_MAX = 50;

    @Autowired
    private CoreTransferInfoService coreTransferInfoService;

    @Autowired
    private CorePaymentService corePaymentService;

    @EventListener
    @Transactional
    public void onPaymentCreated(PaymentCreatedEvent event) {
        TransferInfo target = resolveReceivingAccount(event.adminUsername());
        if (target == null) {
            log.warn("No active transfer account for admin [{}]; payment [{}] gets no QR yet",
                    event.adminUsername(), event.paymentCode());
            return;
        }

        String bankId = resolveBankId(target);
        String accountNo = digitsOnly(target.getTransferNumber());
        if (bankId.isBlank() || accountNo.isBlank()) {
            log.warn("Transfer account [{}] lacks bank id / account number; skipping QR for payment [{}]",
                    target.getTransferCode(), event.paymentCode());
            return;
        }

        String addInfo = buildAddInfo(event.username(), event.planLabel());
        String accountName = upperNoDiacritics(target.getAccountName());
        String amount = event.amount() == null ? "0"
                : event.amount().setScale(0, RoundingMode.HALF_UP).toPlainString();

        String qrUrl = QUICK_LINK_BASE + bankId + "-" + accountNo + "-" + TEMPLATE + ".png"
                + "?amount=" + amount
                + "&addInfo=" + urlEncode(addInfo)
                + "&accountName=" + urlEncode(accountName);

        corePaymentService.updateQr(event.paymentCode(), qrUrl, addInfo);
        log.info("Generated VietQR Quick Link for payment [{}] -> account [{}] amount {}",
                event.paymentCode(), accountNo, amount);
    }

    /**
     * The admin's default ACTIVE account; falls back to the first ACTIVE one.
     */
    private TransferInfo resolveReceivingAccount(String adminUsername) {
        if (adminUsername == null || adminUsername.isBlank()) {
            return null;
        }
        List<TransferInfo> accounts = coreTransferInfoService.findActiveByOwner(adminUsername);
        if (accounts.isEmpty()) {
            return null;
        }
        return accounts.stream().filter(TransferInfo::isDefault).findFirst().orElse(accounts.get(0));
    }

    /**
     * Prefer the stored NAPAS BIN; older rows without a BIN fall back to the
     * issuer name slug, which img.vietqr.io also accepts (e.g. "vietcombank").
     */
    private String resolveBankId(TransferInfo target) {
        if (target.getBankBin() != null && !target.getBankBin().isBlank()) {
            return target.getBankBin().trim();
        }
        return slug(target.getTransferIssuer());
    }

    /**
     * Transfer content: seller name + plan, no diacritics, no special chars,
     * no spaces, capped at 50 characters per the VietQR addInfo constraint.
     */
    private String buildAddInfo(String username, String planLabel) {
        String raw = (username == null ? "" : username) + " " + (planLabel == null ? "" : planLabel);
        String cleaned = upperNoDiacritics(raw).replaceAll("[^A-Z0-9]", "");
        if (cleaned.isBlank()) {
            cleaned = "THANHTOAN";
        }
        return cleaned.length() > ADD_INFO_MAX ? cleaned.substring(0, ADD_INFO_MAX) : cleaned;
    }

    private String upperNoDiacritics(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String slug(String value) {
        return upperNoDiacritics(value).replaceAll("[^A-Z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String urlEncode(String value) {
        // %20 (not '+') for spaces, matching the VietQR Quick Link examples
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
