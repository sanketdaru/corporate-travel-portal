package com.corporate.travel.travel.service;

import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.BookingAudit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BookingAuditService {

    void record(UUID bookingId, String action, Map<String, Object> details, SecurityContext context);

    List<BookingAudit> getAuditTrail(UUID bookingId, SecurityContext context);
}
