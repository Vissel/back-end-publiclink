package com.qrpublic.apartment.core.service;

import com.qrpublic.apartment.repository.TimeZoneRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@Service
public class CoreTimezoneService {

    @Autowired
    TimeZoneRepository timeZoneRepository;

    public CoreTimezoneService(TimeZoneRepository timeZoneRepository) {
        this.timeZoneRepository = timeZoneRepository;
    }

    /**
     * Retrieves the database timezone as a Java TimeZone.
     * Handles offset formats like "+07" or "+07:00" by prefixing with "GMT"
     * since TimeZone.getTimeZone() requires "GMT+07" format for offsets.
     */
    public TimeZone getDatabaseTimeZone() {
        String dbTimezone = timeZoneRepository.getDatabaseTimeZone();
        if (dbTimezone != null && dbTimezone.matches("^[+-]\\d{2}(:\\d{2})?$")) {
            dbTimezone = "GMT" + dbTimezone;
        }
        return TimeZone.getTimeZone(dbTimezone);
    }

    /**
     * Returns current Date in the database timezone.
     */
    public Date getNow() {
        TimeZone dbTimeZone = getDatabaseTimeZone();
        Calendar calendar = Calendar.getInstance(dbTimeZone);
        return calendar.getTime();
    }

    /**
     * Returns the number of milliseconds from now to end of the current day (midnight)
     * in the database timezone.
     */
    public long nowToEndOfDateMili() {
        TimeZone dbTimeZone = getDatabaseTimeZone();
        Calendar now = Calendar.getInstance(dbTimeZone);

        Calendar endOfDay = Calendar.getInstance(dbTimeZone);
        endOfDay.set(Calendar.HOUR_OF_DAY, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 999);

        return endOfDay.getTimeInMillis() - now.getTimeInMillis();
    }
}
