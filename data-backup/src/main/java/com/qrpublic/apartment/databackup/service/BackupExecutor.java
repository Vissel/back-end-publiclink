package com.qrpublic.apartment.databackup.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qrpublic.apartment.databackup.config.BackupProperties;
import com.qrpublic.apartment.databackup.model.BackupResult.SchemaBackupResult;
import com.qrpublic.apartment.databackup.model.BackupResult.TableBackupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPOutputStream;

@Service @Slf4j
public class BackupExecutor {
    private final BackupProperties backupProperties;
    private final WatermarkService watermarkService;
    private final DataSource publiclinkDataSource;
    private final DataSource userDataSource;
    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public BackupExecutor(BackupProperties bp, WatermarkService ws, @Qualifier("publiclinkDataSource") DataSource pds, @Qualifier("userDataSource") DataSource uds) {
        this.backupProperties = bp; this.watermarkService = ws; this.publiclinkDataSource = pds; this.userDataSource = uds;
    }

    private static final Map<String, TblCfg> PL = new LinkedHashMap<>();
    private static final Map<String, TblCfg> US = new LinkedHashMap<>();
    static {
        PL.put("payment_method", new TblCfg("methodId", null, false));
        PL.put("user", new TblCfg(null, "createdAt", false));
        PL.put("request", new TblCfg("reqId", "createdAt", false));
        PL.put("pricing", new TblCfg("pricing_id", "created_at", false));
        PL.put("picture", new TblCfg("picId", null, false));
        PL.put("product", new TblCfg("productId", null, false));
        PL.put("product_picture_map", new TblCfg("map_id", null, false));
        PL.put("sale_environment", new TblCfg(null, "createdAt", false));
        PL.put("order", new TblCfg("orderId", "orderedAt", false));
        PL.put("notification", new TblCfg("id", "created_at", true));
        PL.put("notification_recipient", new TblCfg("id", null, false));
        PL.put("process_message", new TblCfg(null, "createdAt", false));
        US.put("user_tbl", new TblCfg(null, "createdAt", false));
        US.put("profile_tbl", new TblCfg("profileId", null, false));
        US.put("user_auth_tbl", new TblCfg("authId", "createdAt", false));
    }

    public SchemaBackupResult execSchema(String schema, boolean full, String triggerType, String scope) {
        Map<String, TblCfg> tables = "publiclink-db".equals(schema) ? PL : US;
        DataSource ds = "publiclink-db".equals(schema) ? publiclinkDataSource : userDataSource;
        LocalDateTime now = LocalDateTime.now();
        SchemaBackupResult res = SchemaBackupResult.builder().schemaName(schema).tableResults(new ArrayList<>()).build();
        String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String timeStr = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String dir = backupProperties.getStorage().getBasePath() + "/" + schema + "/" + dateStr;
        Map<String, List<Map<String, Object>>> allData = new LinkedHashMap<>();
        try {
            Files.createDirectories(Paths.get(dir));
            for (var entry : tables.entrySet()) {
                String tn = entry.getKey(); TblCfg cfg = entry.getValue();
                try {
                    List<Map<String, Object>> rows = full ? queryAll(ds, tn) : queryIncr(ds, schema, tn, cfg);
                    TableBackupResult tr = TableBackupResult.builder().tableName(tn).rowCount(rows.size()).hasChanges(!rows.isEmpty()).build();
                    if (!rows.isEmpty()) { allData.put(tn, rows); tr.setSizeBytes(om.writeValueAsString(rows).length()); Long mx = cfg.idCol != null ? maxId(rows, cfg.idCol) : null; watermarkService.updateWatermark(schema, tn, now, mx); }
                    res.addTableResult(tr);
                } catch (Exception e) { log.error("Table backup error {}/{}: {}", schema, tn, e.getMessage()); res.addTableResult(TableBackupResult.builder().tableName(tn).rowCount(0).hasChanges(false).build()); }
            }
            String fp = dir + "/" + (full ? "full" : "incr") + "_" + timeStr + ".json.gz";
            long sz = writeFile(fp, allData);
            res.setFilePath(fp); res.setFileSizeBytes(sz);
        } catch (Exception e) { log.error("Schema backup failed {}: {}", schema, e.getMessage()); }
        return res;
    }

    private List<Map<String, Object>> queryAll(DataSource ds, String table) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = ds.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM `" + table + "`")) {
            ResultSetMetaData md = rs.getMetaData(); int cols = md.getColumnCount();
            while (rs.next()) { Map<String, Object> r = new LinkedHashMap<>(); for (int i = 1; i <= cols; i++) r.put(md.getColumnLabel(i), rs.getObject(i)); rows.add(r); }
        } return rows;
    }

    private List<Map<String, Object>> queryIncr(DataSource ds, String schema, String table, TblCfg cfg) throws SQLException {
        Long lastMax = watermarkService.getLastMaxId(schema, table);
        LocalDateTime lastAt = watermarkService.getLastBackupAt(schema, table);
        if (lastMax == null && lastAt == null) return queryAll(ds, table);
        StringBuilder sql = new StringBuilder("SELECT * FROM `" + table + "` WHERE ");
        List<Object> params = new ArrayList<>(); List<String> conds = new ArrayList<>();
        if (cfg.idCol != null && lastMax != null) { conds.add("`" + cfg.idCol + "` > ?"); params.add(lastMax); }
        if (cfg.tsCol != null && lastAt != null) { conds.add("`" + cfg.tsCol + "` > ?"); params.add(Timestamp.valueOf(lastAt)); }
        if (conds.isEmpty()) return queryAll(ds, table);
        sql.append(String.join(" OR ", conds));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) { ResultSetMetaData md = rs.getMetaData(); int cols = md.getColumnCount();
                while (rs.next()) { Map<String, Object> r = new LinkedHashMap<>(); for (int i = 1; i <= cols; i++) r.put(md.getColumnLabel(i), rs.getObject(i)); rows.add(r); } }
        } return rows;
    }

    private Long maxId(List<Map<String, Object>> rows, String col) {
        Long mx = null; for (Map<String, Object> r : rows) { Object v = r.get(col); if (v instanceof Number) { long id = ((Number) v).longValue(); if (mx == null || id > mx) mx = id; } } return mx;
    }

    private long writeFile(String path, Map<String, List<Map<String, Object>>> data) throws IOException {
        Path p = Paths.get(path);
        try (OutputStream os = Files.newOutputStream(p); GZIPOutputStream gz = new GZIPOutputStream(os); Writer w = new OutputStreamWriter(gz)) {
            om.writerWithDefaultPrettyPrinter().writeValue(w, data);
        } return Files.size(p);
    }

    private record TblCfg(String idCol, String tsCol, boolean hasUpdated) {}
}
