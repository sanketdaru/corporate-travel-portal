package com.corporate.travel.consent.service.impl;

import com.corporate.travel.consent.exception.AccessDeniedException;
import com.corporate.travel.consent.exception.ConsentNotFoundException;
import com.corporate.travel.consent.exception.InvalidConsentException;
import com.corporate.travel.consent.model.dto.*;
import com.corporate.travel.consent.model.entity.Consent;
import com.corporate.travel.consent.model.entity.ConsentAudit;
import com.corporate.travel.consent.model.entity.ConsentStatus;
import com.corporate.travel.consent.repository.ConsentAuditRepository;
import com.corporate.travel.consent.repository.ConsentRepository;
import com.corporate.travel.consent.service.ConsentService;
import com.corporate.travel.security.OpaClient;
import com.corporate.travel.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ConsentService with OPA authorization
 */
@Service
@Transactional
public class ConsentServiceImpl implements ConsentService {

    private static final Logger logger = LoggerFactory.getLogger(ConsentServiceImpl.class);

    private final ConsentRepository consentRepository;
    private final ConsentAuditRepository consentAuditRepository;
    private final OpaClient opaClient;

    public ConsentServiceImpl(ConsentRepository consentRepository,
                             ConsentAuditRepository consentAuditRepository,
                             OpaClient opaClient) {
        this.consentRepository = consentRepository;
        this.consentAuditRepository = consentAuditRepository;
        this.opaClient = opaClient;
    }

    @Override
    public ConsentResponse grantConsent(CreateConsentRequest request, SecurityContext context) {
        logger.info("Granting consent: grantor={}, grantee={}, purpose={}",
                request.getGrantorId(), request.getGranteeId(), request.getPurpose());

        // Authorize with OPA
        Map<String, Object> createResource = buildResourceContext(request);
        createResource.put("tenant_id", context.getTenantId());
        if (!opaClient.authorize(context, "create_consent", createResource)) {
            throw new AccessDeniedException("Not authorized to grant consent");
        }

        // Validate request
        validateConsentRequest(request, context);

        // Auto-revoke expired consents for the same pair so they don't block re-grant
        revokeExpiredConsentsForPair(
                request.getGrantorId(), request.getGranteeId(),
                request.getPurpose(), context.getTenantId(), context);

        // Check for duplicate active (non-expired) consent
        if (consentRepository.existsActiveDuplicateConsent(
                request.getGrantorId(),
                request.getGranteeId(),
                request.getPurpose(),
                context.getTenantId())) {
            throw new InvalidConsentException(
                    String.format("Active consent already exists for grantor=%s, grantee=%s, purpose=%s",
                            request.getGrantorId(), request.getGranteeId(), request.getPurpose()));
        }

        // Create consent entity
        Consent consent = Consent.builder()
                .tenantId(context.getTenantId())
                .grantorId(request.getGrantorId())
                .granteeId(request.getGranteeId())
                .delegationId(request.getDelegationId())
                .purpose(request.getPurpose())
                .scopes(request.getScopes())
                .dataCategories(request.getDataCategories())
                .expiresAt(request.getExpiresAt())
                .status(ConsentStatus.ACTIVE)
                .metadata(request.getMetadata())
                .createdBy(context.getActorId())
                .updatedBy(context.getActorId())
                .build();

        // Save consent
        Consent savedConsent = consentRepository.save(consent);
        logger.info("Consent granted: id={}", savedConsent.getId());

        // Create audit record
        createAuditRecord(savedConsent.getId(), "GRANTED", context, 
                Map.of("purpose", request.getPurpose(), "scopes", request.getScopes()));

        return toResponse(savedConsent);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentResponse getConsent(UUID id, SecurityContext context) {
        logger.info("Getting consent: id={}", id);

        Consent consent = consentRepository.findByIdAndTenantId(id, context.getTenantId())
                .orElseThrow(() -> new ConsentNotFoundException(id));

        // Authorize with OPA
        if (!opaClient.authorize(context, "view_consent", buildResourceContext(consent))) {
            throw new AccessDeniedException("Not authorized to view this consent");
        }

        return toResponse(consent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentResponse> getMyConsents(SecurityContext context) {
        logger.info("Getting consents granted by user: userId={}", context.getUserId());

        // Authorize with OPA
        if (!opaClient.authorize(context, "list_my_consents", Map.of("tenant_id", context.getTenantId()))) {
            throw new AccessDeniedException("Not authorized to list consents");
        }

        List<Consent> consents = consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                context.getUserId(), context.getTenantId());

        return consents.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsentsToMe(SecurityContext context) {
        logger.info("Getting consents granted to user: userId={}", context.getUserId());

        // Authorize with OPA
        if (!opaClient.authorize(context, "list_consents_to_me", Map.of("tenant_id", context.getTenantId()))) {
            throw new AccessDeniedException("Not authorized to list consents");
        }

        List<Consent> consents = consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                context.getUserId(), context.getTenantId());

        return consents.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void revokeConsent(UUID id, SecurityContext context) {
        logger.info("Revoking consent: id={}", id);

        Consent consent = consentRepository.findByIdAndTenantId(id, context.getTenantId())
                .orElseThrow(() -> new ConsentNotFoundException(id));

        // Authorize with OPA
        if (!opaClient.authorize(context, "revoke_consent", buildResourceContext(consent))) {
            throw new AccessDeniedException("Not authorized to revoke this consent");
        }

        // Update consent status
        consent.setStatus(ConsentStatus.REVOKED);
        consent.setRevokedAt(LocalDateTime.now());
        consent.setRevokedBy(context.getActorId());
        consent.setUpdatedBy(context.getActorId());

        consentRepository.save(consent);
        logger.info("Consent revoked: id={}", id);

        // Create audit record
        createAuditRecord(id, "REVOKED", context, Map.of("revokedBy", context.getActorId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ValidateConsentResponse validateConsent(ValidateConsentRequest request, SecurityContext context) {
        logger.info("Validating consent: grantor={}, grantee={}, purpose={}",
                request.getGrantorId(), request.getGranteeId(), request.getPurpose());

        // Authorize with OPA
        if (!opaClient.authorize(context, "validate_consent", Map.of("tenant_id", context.getTenantId()))) {
            throw new AccessDeniedException("Not authorized to validate consent");
        }

        // Find active consents
        List<Consent> activeConsents = consentRepository.findActiveConsents(
                request.getGrantorId(),
                request.getGranteeId(),
                request.getPurpose(),
                context.getTenantId()
        );

        if (activeConsents.isEmpty()) {
            return ValidateConsentResponse.builder()
                    .valid(false)
                    .reason("No active consent found")
                    .build();
        }

        // Check if any consent covers all required scopes
        for (Consent consent : activeConsents) {
            if (consent.hasAllScopes(request.getScopes())) {
                // Record consent usage in audit
                createAuditRecord(consent.getId(), "USED", context,
                        Map.of("purpose", request.getPurpose(), "scopes", request.getScopes()));

                return ValidateConsentResponse.builder()
                        .valid(true)
                        .consentId(consent.getId())
                        .build();
            }
        }

        // No consent covers all scopes - calculate missing scopes
        List<String> missingScopes = request.getScopes().stream()
                .filter(scope -> activeConsents.stream()
                        .noneMatch(c -> c.hasScope(scope)))
                .collect(Collectors.toList());

        return ValidateConsentResponse.builder()
                .valid(false)
                .reason("Consent does not cover all required scopes")
                .missingScopes(missingScopes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsentAuditResponse> getConsentAuditTrail(UUID consentId, SecurityContext context) {
        logger.info("Getting audit trail for consent: id={}", consentId);

        // Verify consent exists and user has access
        Consent consent = consentRepository.findByIdAndTenantId(consentId, context.getTenantId())
                .orElseThrow(() -> new ConsentNotFoundException(consentId));

        // Authorize with OPA
        if (!opaClient.authorize(context, "view_consent_audit", buildResourceContext(consent))) {
            throw new AccessDeniedException("Not authorized to view consent audit trail");
        }

        List<ConsentAudit> auditRecords = consentAuditRepository
                .findByConsentIdAndTenantIdOrderByTimestampDesc(consentId, context.getTenantId());

        return auditRecords.stream()
                .map(this::toAuditResponse)
                .collect(Collectors.toList());
    }

    // Private helper methods

    /**
     * Auto-revoke expired consents for the same grantor-grantee-purpose triple so they
     * don't block re-granting consent after a delegation is renewed.
     */
    private void revokeExpiredConsentsForPair(
            String grantorId, String granteeId, String purpose, String tenantId, SecurityContext context) {
        List<Consent> expired = consentRepository.findExpiredConsentsForPair(
                grantorId, granteeId, purpose, tenantId);
        if (expired.isEmpty()) {
            return;
        }
        logger.info("Auto-revoking {} expired consent(s) for pair grantor={}, grantee={}, purpose={}",
                expired.size(), grantorId, granteeId, purpose);
        for (Consent c : expired) {
            c.setStatus(ConsentStatus.REVOKED);
            c.setRevokedAt(LocalDateTime.now());
            c.setRevokedBy(context.getActorId());
            c.setUpdatedBy(context.getActorId());
            consentRepository.save(c);
            createAuditRecord(c.getId(), "AUTO_REVOKED", context,
                    Map.of("reason", "expired_before_re_grant"));
        }
    }

    private void validateConsentRequest(CreateConsentRequest request, SecurityContext context) {
        if (request.getGrantorId().equals(request.getGranteeId())) {
            throw new InvalidConsentException("Grantor and grantee cannot be the same");
        }

        if (request.getScopes() == null || request.getScopes().isEmpty()) {
            throw new InvalidConsentException("Scopes cannot be empty");
        }

        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidConsentException("Expiration date cannot be in the past");
        }
    }

    private void createAuditRecord(UUID consentId, String action, SecurityContext context, Map<String, Object> details) {
        ConsentAudit audit = ConsentAudit.builder()
                .consentId(consentId)
                .action(action)
                .actorId(context.getActorId())
                .subjectId(context.getSubjectId())
                .tenantId(context.getTenantId())
                .details(details)
                .build();

        consentAuditRepository.save(audit);
    }

    private ConsentResponse toResponse(Consent consent) {
        return ConsentResponse.builder()
                .id(consent.getId())
                .tenantId(consent.getTenantId())
                .grantorId(consent.getGrantorId())
                .granteeId(consent.getGranteeId())
                .delegationId(consent.getDelegationId())
                .purpose(consent.getPurpose())
                .scopes(consent.getScopes())
                .dataCategories(consent.getDataCategories())
                .grantedAt(consent.getGrantedAt())
                .expiresAt(consent.getExpiresAt())
                .revokedAt(consent.getRevokedAt())
                .revokedBy(consent.getRevokedBy())
                .status(consent.getStatus())
                .metadata(consent.getMetadata())
                .createdBy(consent.getCreatedBy())
                .createdAt(consent.getCreatedAt())
                .updatedBy(consent.getUpdatedBy())
                .updatedAt(consent.getUpdatedAt())
                .valid(consent.isValid())
                .build();
    }

    private ConsentAuditResponse toAuditResponse(ConsentAudit audit) {
        return ConsentAuditResponse.builder()
                .id(audit.getId())
                .consentId(audit.getConsentId())
                .action(audit.getAction())
                .actorId(audit.getActorId())
                .subjectId(audit.getSubjectId())
                .timestamp(audit.getTimestamp())
                .details(audit.getDetails())
                .tenantId(audit.getTenantId())
                .build();
    }

    private Map<String, Object> buildResourceContext(Object resource) {
        Map<String, Object> context = new HashMap<>();
        
        if (resource instanceof CreateConsentRequest) {
            CreateConsentRequest request = (CreateConsentRequest) resource;
            context.put("grantor_id", request.getGrantorId());
            context.put("grantee_id", request.getGranteeId());
            context.put("purpose", request.getPurpose());
        } else if (resource instanceof Consent) {
            Consent consent = (Consent) resource;
            context.put("consent_id", consent.getId());
            context.put("grantor_id", consent.getGrantorId());
            context.put("grantee_id", consent.getGranteeId());
            context.put("tenant_id", consent.getTenantId());
        }
        
        return context;
    }
}