package com.qrpublic.apartment.databackup.config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean public DataSource publiclinkDataSource(@Value("${backup.source.publiclink.url}") String u, @Value("${backup.source.publiclink.username}") String un, @Value("${backup.source.publiclink.password}") String p) {
        HikariConfig c = new HikariConfig(); c.setJdbcUrl(u); c.setUsername(un); c.setPassword(p); c.setPoolName("publiclink-pool"); c.setMaximumPoolSize(5); c.setMinimumIdle(1); c.setReadOnly(true); return new HikariDataSource(c);
    }
    @Bean public DataSource userDataSource(@Value("${backup.source.user.url}") String u, @Value("${backup.source.user.username}") String un, @Value("${backup.source.user.password}") String p) {
        HikariConfig c = new HikariConfig(); c.setJdbcUrl(u); c.setUsername(un); c.setPassword(p); c.setPoolName("user-pool"); c.setMaximumPoolSize(5); c.setMinimumIdle(1); c.setReadOnly(true); return new HikariDataSource(c);
    }
}
