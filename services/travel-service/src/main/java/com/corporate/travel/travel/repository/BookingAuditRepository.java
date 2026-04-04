package com.corporate.travel.travel.repository;

import com.corporate.travel.travel.model.entity.BookingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BookingAuditRepository extends JpaRepository<BookingAudit, UUID> {

    List<BookingAudit> findByBookingIdAndTenantIdOrderByTimestampDesc(UUID bookingId, String tenantId);

    List<BookingAudit> findByTenantIdOrderByTimestampDesc(String tenantId);
}
