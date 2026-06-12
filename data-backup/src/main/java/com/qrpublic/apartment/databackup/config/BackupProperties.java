package com.qrpublic.apartment.databackup.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "backup")
@Getter
@Setter
public class BackupProperties {

    private Storage storage = new Storage();
    private Otp otp = new Otp();
    private Token token = new Token();
    private Schedule schedule = new Schedule();
    private Publiclink publiclink = new Publiclink();

    @Getter
    @Setter
    public static class Storage {
        private String basePath = "/var/backups/publiclink";
    }

    @Getter
    @Setter
    public static class Otp {
        private int ttlMinutes = 2;
        private String recipient = "jelly1512@proton.me";
    }

    @Getter
    @Setter
    public static class Token {
        private int ttlMinutes = 5;
    }

    @Getter
    @Setter
    public static class Schedule {
        private String dailyCron = "0 0 0 * * *";
        private String weeklyCron = "0 0 0 * * MON";
        private String monthlyCron = "0 0 0 1 * *";
    }

    @Getter
    @Setter
    public static class Publiclink {
        private String serviceUrl = "http://localhost:9080/publiclink";
    }
}
