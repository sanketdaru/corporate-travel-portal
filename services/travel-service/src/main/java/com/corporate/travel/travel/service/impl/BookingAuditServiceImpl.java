package com.corporate.travel.travel.service.impl;

import com.corporate.travel.security.SecurityContext;
import com.corporate.travel.travel.model.entity.BookingAudit;
import com.corporate.travel.travel.repository.BookingAuditRepository;
import com.corporate.travel.travel.service.BookingAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingAuditServiceImpl implements BookingAuditService {

    private final BookingAuditRepository auditRepository;

    /**
     * Records a booking audit event within the caller's transaction.
     * Must share the same transaction as the booking write so the FK
     * booking_audit.booking_id → bookings.id is satisfied before commit.
     */
    @Override
    @Transactional
    public void record(UUID bookingId, String action, Map<String, Object> details, SecurityContext context) {
        String actorId   = context.getActorId() != null ? context.getActorId() : context.getUserId();
        String subjectId = context.getSubjectId() != null ? context.getSubjectId() : context.getUserId();

        UUID delegationId = null;
        if (context.getDelegationId() != null) {
            try { delegationId = UUID.fromString(context.getDelegationId()); } catch (IllegalArgumentException ignored) {}
        }

        UUID consentId = null;
        if (context.getConsentId() != null) {
            try { consentId = UUID.fromString(context.getConsentId()); } catch (IllegalArgumentException ignored) {}
        }

        BookingAudit audit = BookingAudit.builder()
                .bookingId(bookingId)
                .actorId(actorId)
                .subjectId(subjectId)
                .action(action)
                .details(details)
                .tenantId(context.getTenantId())
                .delegationId(delegationId)
                .consentId(consentId)
                .build();

        auditRepository.save(audit);
        log.debug("Audit recorded: booking={} action={} actor={} subject={}", bookingId, action, actorId, subjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingAudit> getAuditTrail(UUID bookingId, SecurityContext context) {
        return auditRepository.findByBookingIdAndTenantIdOrderByTimestampDesc(bookingId, context.getTenantId());
    }
}
