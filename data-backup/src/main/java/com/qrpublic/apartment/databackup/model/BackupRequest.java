package com.qrpublic.apartment.databackup.model;
import lombok.*;
import java.util.List;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BackupRequest { private String scope; private List<String> schemas; }
