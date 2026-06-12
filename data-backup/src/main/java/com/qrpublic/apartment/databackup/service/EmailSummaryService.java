package com.qrpublic.apartment.databackup.service;
import com.qrpublic.apartment.databackup.config.BackupProperties;
import com.qrpublic.apartment.databackup.model.BackupResult;
import com.qrpublic.apartment.databackup.model.BackupResult.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class EmailSummaryService {
    private final JavaMailSender mailSender;
    private final BackupProperties backupProperties;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void sendSummary(BackupResult r) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(backupProperties.getOtp().getRecipient());
            h.setSubject(String.format("[Data Backup] %s - %s - %s", r.getStatus(), r.getScope(), r.getStartedAt() != null ? r.getStartedAt().format(FMT) : "N/A"));
            h.setText(buildHtml(r), true);
            mailSender.send(msg);
            log.info("Backup summary email sent");
        } catch (Exception e) { log.error("Email send failed: {}", e.getMessage()); }
    }

    private String buildHtml(BackupResult r) {
        StringBuilder h = new StringBuilder();
        h.append("<html><body style='font-family:monospace;background:#f5f5f5;padding:20px'><div style='max-width:700px;margin:0 auto;background:white;padding:24px;border-radius:8px'>");
        h.append("<h2 style='border-bottom:2px solid #007bff;padding-bottom:8px'>Backup Summary</h2>");
        h.append("<table style='width:100%;border-collapse:collapse'>");
        h.append(row("Trigger ID", String.valueOf(r.getTriggerId())));
        h.append(row("Type", r.getTriggerType() + (r.getScheduleType() != null ? " (" + r.getScheduleType() + ")" : "")));
        h.append(row("Scope", r.getScope()));
        h.append(row("Started", fmt(r.getStartedAt())));
        h.append(row("Completed", fmt(r.getCompletedAt())));
        h.append(row("Duration", r.getDurationSeconds() + "s"));
        h.append("<tr><td>Status</td><td style='font-weight:bold;color:").append("COMPLETED".equals(r.getStatus()) ? "#28a745" : "#dc3545").append("'>").append(r.getStatus()).append("</td></tr>");
        h.append("</table>");
        for (SchemaBackupResult sr : r.getSchemaResults()) {
            h.append("<h3>").append(sr.getSchemaName()).append("</h3><table style='width:100%;border-collapse:collapse'><tr><th style='text-align:left;padding:8px;background:#f8f9fa'>Table</th><th style='text-align:left;padding:8px;background:#f8f9fa'>Rows</th><th style='text-align:left;padding:8px;background:#f8f9fa'>Size</th></tr>");
            long unchanged = 0;
            for (TableBackupResult t : sr.getTableResults()) {
                if (t.isHasChanges()) h.append("<tr><td style='padding:8px;border-bottom:1px solid #eee'>").append(t.getTableName()).append("</td><td style='padding:8px;border-bottom:1px solid #eee'>").append(t.getRowCount()).append("</td><td style='padding:8px;border-bottom:1px solid #eee'>").append(fmtBytes(t.getSizeBytes())).append("</td></tr>");
                else unchanged++;
            }
            h.append("</table>");
            if (unchanged > 0) h.append("<p style='color:#999;font-style:italic'>(").append(unchanged).append(" unchanged table(s) skipped)</p>");
        }
        h.append("<div style='background:#f8f9fa;padding:12px;border-radius:4px;margin-top:16px'><strong>Storage</strong><br/>");
        for (SchemaBackupResult sr : r.getSchemaResults()) { if (sr.getFilePath() != null) h.append("File: <code>").append(sr.getFilePath()).append("</code><br/>"); }
        h.append("Total: <strong>").append(fmtBytes(r.getTotalFileSizeBytes())).append("</strong></div>");
        if ("FAILED".equals(r.getStatus()) && r.getErrorMessage() != null) h.append("<div style='background:#fff3cd;padding:12px;border-radius:4px;margin-top:16px;border-left:4px solid #ffc107'><strong>Error:</strong><pre>").append(r.getErrorMessage()).append("</pre></div>");
        h.append("</div></body></html>");
        return h.toString();
    }
    private String row(String l, String v) { return "<tr><td style='color:#666;padding:4px 0'>" + l + "</td><td style='padding:4px 0'>" + v + "</td></tr>"; }
    private String fmt(java.time.LocalDateTime dt) { return dt != null ? dt.format(FMT) : "-"; }
    private String fmtBytes(long b) { if (b < 1024) return b + " B"; if (b < 1048576) return String.format("%.1f KB", b/1024.0); return String.format("%.1f MB", b/1048576.0); }
}
