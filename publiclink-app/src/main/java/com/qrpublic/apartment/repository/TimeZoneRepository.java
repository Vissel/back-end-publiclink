package com.qrpublic.apartment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class TimeZoneRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Retrieves the current database session timezone.
     * If the session timezone is "SYSTEM", it falls back to the system timezone.
     *
     * @return the resolved database timezone as a String (e.g. "Asia/Ho_Chi_Minh", "UTC", "+07:00")
     */
    public String getDatabaseTimeZone() {
        String sessionTz = (String) entityManager.createNativeQuery("SELECT @@session.time_zone")
                .getSingleResult();
        if ("SYSTEM".equalsIgnoreCase(sessionTz)) {
            return (String) entityManager.createNativeQuery("SELECT @@system_time_zone")
                    .getSingleResult();
        }
        return sessionTz;
    }
}
