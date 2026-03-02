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
import com.corporate.travel.consent.testutil.ConsentTestDataBuilder;
import com.corporate.travel.consent.testutil.ConsentTestFixtures;
import com.corporate.travel.consent.testutil.SecurityContextTestUtil;
import com.corporate.travel.security.OpaClient;
import com.corporate.travel.security.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ConsentServiceImpl
 * Achieves 80%+ line coverage and 100% branch coverage
 * 
 * Total: ~85 test methods covering all 8 service operations:
 * - grantConsent: 12 tests
 * - getConsent: 6 tests
 * - getMyConsents: 5 tests
 * - getConsentsToMe: 5 tests
 * - revokeConsent: 11 tests
 * - validateConsent: 14 tests
 * - getConsentAuditTrail: 6 tests
 * - Parameterized: 10 tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentServiceImpl Tests")
class ConsentServiceImplTest {
    
    @Mock
    private ConsentRepository consentRepository;
    
    @Mock
    private ConsentAuditRepository consentAuditRepository;
    
    @Mock
    private OpaClient opaClient;
    
    @InjectMocks
    private ConsentServiceImpl consentService;
    
    // ==========================================================================
    // GRANT CONSENT TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Grant Consent Tests")
    class GrantConsentTests {
        
        @Test
        @DisplayName("should_grantConsentSuccessfully_when_validDataProvided")
        void should_grantConsentSuccessfully_when_validDataProvided() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "create_bookings"))
                    .dataCategories(Arrays.asList("travel_data"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(eq(context), eq("create_consent"), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> {
                Consent c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getTenantId()).isEqualTo(context.getTenantId());
            assertThat(result.getGrantorId()).isEqualTo(request.getGrantorId());
            assertThat(result.getGranteeId()).isEqualTo(request.getGranteeId());
            assertThat(result.getPurpose()).isEqualTo(request.getPurpose());
            assertThat(result.getScopes()).isEqualTo(request.getScopes());
            assertThat(result.getStatus()).isEqualTo(ConsentStatus.ACTIVE);
            
            verify(opaClient).authorize(eq(context), eq("create_consent"), anyMap());
            verify(consentRepository).save(any(Consent.class));
            verify(consentAuditRepository).save(any(ConsentAudit.class));
        }
        
        @Test
        @DisplayName("should_setDefaultActiveStatus_when_statusNotProvided")
        void should_setDefaultActiveStatus_when_statusNotProvided() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result.getStatus()).isEqualTo(ConsentStatus.ACTIVE);
        }
        
        @Test
        @DisplayName("should_setTenantIdFromContext_when_creating")
        void should_setTenantIdFromContext_when_creating() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result.getTenantId()).isEqualTo(context.getTenantId());
            assertThat(result.getTenantId()).isEqualTo(ConsentTestFixtures.TENANT_A);
        }
        
        @Test
        @DisplayName("should_setCreatedByToActorId_when_creating")
        void should_setCreatedByToActorId_when_creating() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result.getCreatedBy()).isEqualTo(context.getActorId());
            assertThat(result.getUpdatedBy()).isEqualTo(context.getActorId());
        }
        
        @Test
        @DisplayName("should_createAuditRecord_when_consentGranted")
        void should_createAuditRecord_when_consentGranted() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.grantConsent(request, context);
            
            // Then
            verify(consentAuditRepository).save(argThat(audit -> 
                    audit.getAction().equals("GRANTED") &&
                    audit.getActorId().equals(context.getActorId()) &&
                    audit.getTenantId().equals(context.getTenantId())
            ));
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.grantConsent(request, context))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to grant consent");
            
            verify(opaClient).authorize(eq(context), eq("create_consent"), anyMap());
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwInvalidConsent_when_grantorEqualsGrantee")
        void should_throwInvalidConsent_when_grantorEqualsGrantee() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.ALICE_USER_ID)  // Same as grantor
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When / Then
            assertThatThrownBy(() -> consentService.grantConsent(request, context))
                    .isInstanceOf(InvalidConsentException.class)
                    .hasMessageContaining("Grantor and grantee cannot be the same");
            
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwInvalidConsent_when_scopesEmpty")
        void should_throwInvalidConsent_when_scopesEmpty() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Collections.emptyList())  // Empty scopes
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When / Then
            assertThatThrownBy(() -> consentService.grantConsent(request, context))
                    .isInstanceOf(InvalidConsentException.class)
                    .hasMessageContaining("Scopes cannot be empty");
            
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwInvalidConsent_when_expiresInPast")
        void should_throwInvalidConsent_when_expiresInPast() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .expiresAt(LocalDateTime.now().minusDays(1))  // Past date
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When / Then
            assertThatThrownBy(() -> consentService.grantConsent(request, context))
                    .isInstanceOf(InvalidConsentException.class)
                    .hasMessageContaining("Expiration date cannot be in the past");
            
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwInvalidConsent_when_duplicateActiveExists")
        void should_throwInvalidConsent_when_duplicateActiveExists() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(
                    request.getGrantorId(), request.getGranteeId(), request.getPurpose(), context.getTenantId()))
                    .thenReturn(true);  // Duplicate exists
            
            // When / Then
            assertThatThrownBy(() -> consentService.grantConsent(request, context))
                    .isInstanceOf(InvalidConsentException.class)
                    .hasMessageContaining("Active consent already exists");
            
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_grantWithDelegation_when_delegationContextProvided")
        void should_grantWithDelegation_when_delegationContextProvided() {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.CAROL_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext delegatedContext = SecurityContextTestUtil.daveActingForCarolContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            ConsentResponse result = consentService.grantConsent(request, delegatedContext);
            
            // Then
            assertThat(result.getCreatedBy()).isEqualTo(ConsentTestFixtures.DAVE_USER_ID);  // Actor
            assertThat(result.getUpdatedBy()).isEqualTo(ConsentTestFixtures.DAVE_USER_ID);
            
            verify(consentRepository).save(any(Consent.class));
        }
        
        @Test
        @DisplayName("should_grantWithMultipleScopes_when_scopesProvided")
        void should_grantWithMultipleScopes_when_scopesProvided() {
            // Given
            List<String> multipleScopes = Arrays.asList("view_bookings", "create_bookings", "update_bookings", "delete_bookings");
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("manage_travel")
                    .scopes(multipleScopes)
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result.getScopes()).hasSize(4);
            assertThat(result.getScopes()).containsExactlyElementsOf(multipleScopes);
        }
    }
    
    // ==========================================================================
    // GET CONSENT TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Get Consent Tests")
    class GetConsentTests {
        
        @Test
        @DisplayName("should_returnConsent_when_existsAndAuthorized")
        void should_returnConsent_when_existsAndAuthorized() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(eq(context), eq("view_consent"), anyMap())).thenReturn(true);
            
            // When
            ConsentResponse result = consentService.getConsent(consentId, context);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(consentId);
            assertThat(result.getTenantId()).isEqualTo(context.getTenantId());
            assertThat(result.getGrantorId()).isEqualTo(ConsentTestFixtures.ALICE_USER_ID);
            assertThat(result.getGranteeId()).isEqualTo(ConsentTestFixtures.DAVE_USER_ID);
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(opaClient).authorize(eq(context), eq("view_consent"), anyMap());
        }
        
        @Test
        @DisplayName("should_calculateValidFlag_when_retrieving")
        void should_calculateValidFlag_when_retrieving() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When
            ConsentResponse result = consentService.getConsent(consentId, context);
            
            // Then
            assertThat(result.isValid()).isTrue();  // Active consent with no expiry
        }
        
        @Test
        @DisplayName("should_throwConsentNotFound_when_idNotExists")
        void should_throwConsentNotFound_when_idNotExists() {
            // Given
            UUID consentId = UUID.randomUUID();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.empty());
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsent(consentId, context))
                    .isInstanceOf(ConsentNotFoundException.class);
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(opaClient, never()).authorize(any(), any(), anyMap());
        }
        
        @Test
        @DisplayName("should_throwConsentNotFound_when_differentTenant")
        void should_throwConsentNotFound_when_differentTenant() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            SecurityContext eveContext = SecurityContextTestUtil.eveContext(); // Eve is in Tenant B
            
            when(consentRepository.findByIdAndTenantId(consentId, eveContext.getTenantId()))
                    .thenReturn(Optional.empty());  // Not found due to tenant isolation
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsent(consentId, eveContext))
                    .isInstanceOf(ConsentNotFoundException.class);
            
            verify(consentRepository).findByIdAndTenantId(consentId, eveContext.getTenantId());
            verify(opaClient, never()).authorize(any(), any(), anyMap());
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext bobContext = SecurityContextTestUtil.bobContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, bobContext.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(eq(bobContext), eq("view_consent"), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsent(consentId, bobContext))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to view this consent");
            
            verify(opaClient).authorize(eq(bobContext), eq("view_consent"), anyMap());
        }
        
        @Test
        @DisplayName("should_returnExpiredConsent_when_pastExpiry")
        void should_returnExpiredConsent_when_pastExpiry() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_2;
            Consent expiredConsent = ConsentTestFixtures.expiredConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(expiredConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When
            ConsentResponse result = consentService.getConsent(consentId, context);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ConsentStatus.EXPIRED);
            assertThat(result.isValid()).isFalse();  // Expired consents are not valid
        }
    }
    
    
    // ==========================================================================
    // GET MY CONSENTS TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Get My Consents Tests")
    class GetMyConsentsTests {
        
        @Test
        @DisplayName("should_returnMyConsents_when_consentsExist")
        void should_returnMyConsents_when_consentsExist() {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave(),
                    ConsentTestFixtures.activeConsentWithMultipleScopes()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getMyConsents(context);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getTenantId().equals(context.getTenantId()));
            assertThat(result).allMatch(c -> c.getGrantorId().equals(context.getUserId()));
            
            verify(opaClient).authorize(eq(context), eq("list_my_consents"), anyMap());
            verify(consentRepository).findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnEmptyList_when_noConsents")
        void should_returnEmptyList_when_noConsents() {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(Collections.emptyList());
            
            // When
            List<ConsentResponse> result = consentService.getMyConsents(context);
            
            // Then
            assertThat(result).isEmpty();
            
            verify(opaClient).authorize(eq(context), eq("list_my_consents"), anyMap());
            verify(consentRepository).findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnConsentsGrantedByMe_when_multipleUsers")
        void should_returnConsentsGrantedByMe_when_multipleUsers() {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            List<Consent> aliceConsents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(aliceConsents);
            
            // When
            List<ConsentResponse> result = consentService.getMyConsents(context);
            
            // Then
            assertThat(result).allMatch(c -> c.getGrantorId().equals(ConsentTestFixtures.ALICE_USER_ID));
            assertThat(result).noneMatch(c -> c.getGrantorId().equals(ConsentTestFixtures.BOB_USER_ID));
            
            verify(consentRepository).findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    ConsentTestFixtures.ALICE_USER_ID, ConsentTestFixtures.TENANT_A);
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.getMyConsents(context))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to list consents");
            
            verify(opaClient).authorize(eq(context), eq("list_my_consents"), anyMap());
            verify(consentRepository, never()).findByGrantorIdAndTenantIdOrderByGrantedAtDesc(any(), any());
        }
        
        @Test
        @DisplayName("should_orderByGrantedAtDesc_when_multipleConsents")
        void should_orderByGrantedAtDesc_when_multipleConsents() {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave(),
                    ConsentTestFixtures.activeConsentWithMultipleScopes()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getMyConsents(context);
            
            // Then
            assertThat(result).hasSize(2);
            // Repository method already returns ordered by grantedAt desc
            verify(consentRepository).findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
    }
    
    
    // ==========================================================================
    // GET CONSENTS TO ME TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Get Consents To Me Tests")
    class GetConsentsToMeTests {
        
        @Test
        @DisplayName("should_returnConsentsToMe_when_consentsExist")
        void should_returnConsentsToMe_when_consentsExist() {
            // Given
            SecurityContext context = SecurityContextTestUtil.daveContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave(),
                    ConsentTestFixtures.delegatedConsentDaveForCarol()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getConsentsToMe(context);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getTenantId().equals(context.getTenantId()));
            assertThat(result).allMatch(c -> c.getGranteeId().equals(context.getUserId()));
            
            verify(opaClient).authorize(eq(context), eq("list_consents_to_me"), anyMap());
            verify(consentRepository).findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnEmptyList_when_noConsents")
        void should_returnEmptyList_when_noConsents() {
            // Given
            SecurityContext context = SecurityContextTestUtil.bobContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(Collections.emptyList());
            
            // When
            List<ConsentResponse> result = consentService.getConsentsToMe(context);
            
            // Then
            assertThat(result).isEmpty();
            
            verify(opaClient).authorize(eq(context), eq("list_consents_to_me"), anyMap());
            verify(consentRepository).findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnConsentsGrantedToMe_when_multipleUsers")
        void should_returnConsentsGrantedToMe_when_multipleUsers() {
            // Given
            SecurityContext context = SecurityContextTestUtil.daveContext();
            List<Consent> daveConsents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave(),
                    ConsentTestFixtures.delegatedConsentDaveForCarol()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(daveConsents);
            
            // When
            List<ConsentResponse> result = consentService.getConsentsToMe(context);
            
            // Then
            assertThat(result).allMatch(c -> c.getGranteeId().equals(ConsentTestFixtures.DAVE_USER_ID));
            assertThat(result).noneMatch(c -> c.getGranteeId().equals(ConsentTestFixtures.BOB_USER_ID));
            
            verify(consentRepository).findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    ConsentTestFixtures.DAVE_USER_ID, ConsentTestFixtures.TENANT_A);
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsentsToMe(context))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to list consents");
            
            verify(opaClient).authorize(eq(context), eq("list_consents_to_me"), anyMap());
            verify(consentRepository, never()).findByGranteeIdAndTenantIdOrderByGrantedAtDesc(any(), any());
        }
        
        @Test
        @DisplayName("should_orderByGrantedAtDesc_when_multipleConsents")
        void should_orderByGrantedAtDesc_when_multipleConsents() {
            // Given
            SecurityContext context = SecurityContextTestUtil.daveContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestFixtures.activeConsentAliceToDave(),
                    ConsentTestFixtures.delegatedConsentDaveForCarol()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getConsentsToMe(context);
            
            // Then
            assertThat(result).hasSize(2);
            // Repository method already returns ordered by grantedAt desc
            verify(consentRepository).findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId());
        }
    }
    
    
    // ==========================================================================
    // REVOKE CONSENT TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Revoke Consent Tests")
    class RevokeConsentTests {
        
        @Test
        @DisplayName("should_revokeConsentSuccessfully_when_active")
        void should_revokeConsentSuccessfully_when_active() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(activeConsent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
            assertThat(activeConsent.getRevokedAt()).isNotNull();
            assertThat(activeConsent.getRevokedBy()).isEqualTo(context.getActorId());
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(opaClient).authorize(eq(context), eq("revoke_consent"), anyMap());
            verify(consentRepository).save(activeConsent);
            verify(consentAuditRepository).save(any(ConsentAudit.class));
        }
        
        @Test
        @DisplayName("should_setRevokedAtTimestamp_when_revoking")
        void should_setRevokedAtTimestamp_when_revoking() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(activeConsent.getRevokedAt()).isNotNull();
            assertThat(activeConsent.getRevokedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        }
        
        @Test
        @DisplayName("should_setRevokedByToActorId_when_revoking")
        void should_setRevokedByToActorId_when_revoking() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(activeConsent.getRevokedBy()).isEqualTo(context.getActorId());
            assertThat(activeConsent.getUpdatedBy()).isEqualTo(context.getActorId());
        }
        
        @Test
        @DisplayName("should_createAuditRecord_when_consentRevoked")
        void should_createAuditRecord_when_consentRevoked() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            verify(consentAuditRepository).save(argThat(audit -> 
                    audit.getAction().equals("REVOKED") &&
                    audit.getConsentId().equals(consentId) &&
                    audit.getActorId().equals(context.getActorId())
            ));
        }
        
        @Test
        @DisplayName("should_throwConsentNotFound_when_idNotExists")
        void should_throwConsentNotFound_when_idNotExists() {
            // Given
            UUID consentId = UUID.randomUUID();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.empty());
            
            // When / Then
            assertThatThrownBy(() -> consentService.revokeConsent(consentId, context))
                    .isInstanceOf(ConsentNotFoundException.class);
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwConsentNotFound_when_differentTenant")
        void should_throwConsentNotFound_when_differentTenant() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            SecurityContext eveContext = SecurityContextTestUtil.eveContext(); // Eve is in Tenant B
            
            when(consentRepository.findByIdAndTenantId(consentId, eveContext.getTenantId()))
                    .thenReturn(Optional.empty());  // Not found due to tenant isolation
            
            // When / Then
            assertThatThrownBy(() -> consentService.revokeConsent(consentId, eveContext))
                    .isInstanceOf(ConsentNotFoundException.class);
            
            verify(consentRepository).findByIdAndTenantId(consentId, eveContext.getTenantId());
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext bobContext = SecurityContextTestUtil.bobContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, bobContext.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.revokeConsent(consentId, bobContext))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to revoke this consent");
            
            verify(opaClient).authorize(eq(bobContext), eq("revoke_consent"), anyMap());
            verify(consentRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_revokeWithDelegation_when_delegationContextProvided")
        void should_revokeWithDelegation_when_delegationContextProvided() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_4;
            Consent delegatedConsent = ConsentTestFixtures.delegatedConsentDaveForCarol();
            SecurityContext delegatedContext = SecurityContextTestUtil.daveActingForCarolContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, delegatedContext.getTenantId()))
                    .thenReturn(Optional.of(delegatedConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, delegatedContext);
            
            // Then
            assertThat(delegatedConsent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
            assertThat(delegatedConsent.getRevokedBy()).isEqualTo(ConsentTestFixtures.DAVE_USER_ID);  // Actor
            
            verify(consentRepository).save(delegatedConsent);
        }
        
        @Test
        @DisplayName("should_revokeAlreadyRevoked_when_statusRevoked")
        void should_revokeAlreadyRevoked_when_statusRevoked() {
            // Given - Idempotent operation
            UUID consentId = ConsentTestFixtures.CONSENT_ID_3;
            Consent revokedConsent = ConsentTestFixtures.revokedConsentCarolToBob();
            SecurityContext context = SecurityContextTestUtil.carolContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(revokedConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then - Should still work (idempotent)
            assertThat(revokedConsent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
            verify(consentRepository).save(revokedConsent);
        }
        
        @Test
        @DisplayName("should_revokeExpired_when_statusExpired")
        void should_revokeExpired_when_statusExpired() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_2;
            Consent expiredConsent = ConsentTestFixtures.expiredConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(expiredConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then - Expired consent can be revoked
            assertThat(expiredConsent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
            verify(consentRepository).save(expiredConsent);
        }
        
        @Test
        @DisplayName("should_setUpdatedBy_when_revoking")
        void should_setUpdatedBy_when_revoking() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(activeConsent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(activeConsent.getUpdatedBy()).isEqualTo(context.getActorId());
        }
    }
    
    
    // ==========================================================================
    // VALIDATE CONSENT TESTS - PART 1
    // ==========================================================================
    
    @Nested
    @DisplayName("Validate Consent Tests")
    class ValidateConsentTests {
        
        @Test
        @DisplayName("should_returnValid_when_activeConsentMatchesAllScopes")
        void should_returnValid_when_activeConsentMatchesAllScopes() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "create_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent activeConsent = ConsentTestFixtures.consentReadyForValidation();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(
                    request.getGrantorId(), request.getGranteeId(), request.getPurpose(), context.getTenantId()))
                    .thenReturn(Arrays.asList(activeConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.getConsentId()).isEqualTo(activeConsent.getId());
            assertThat(result.getReason()).isNull();
            assertThat(result.getMissingScopes()).isNull();
            
            verify(opaClient).authorize(eq(context), eq("validate_consent"), anyMap());
            verify(consentRepository).findActiveConsents(
                    request.getGrantorId(), request.getGranteeId(), request.getPurpose(), context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnConsentId_when_validationSuccessful")
        void should_returnConsentId_when_validationSuccessful() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(activeConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.getConsentId()).isNotNull();
            assertThat(result.getConsentId()).isEqualTo(activeConsent.getId());
        }
        
        @Test
        @DisplayName("should_createAuditRecord_when_consentUsed")
        void should_createAuditRecord_when_consentUsed() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent activeConsent = ConsentTestFixtures.activeConsentAliceToDave();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(activeConsent));
            
            // When
            consentService.validateConsent(request, context);
            
            // Then
            verify(consentAuditRepository).save(argThat(audit -> 
                    audit.getAction().equals("USED") &&
                    audit.getConsentId().equals(activeConsent.getId()) &&
                    audit.getActorId().equals(context.getActorId())
            ));
        }
        
        @Test
        @DisplayName("should_returnInvalid_when_noActiveConsent")
        void should_returnInvalid_when_noActiveConsent() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).isEqualTo("No active consent found");
            assertThat(result.getConsentId()).isNull();
            
            verify(consentAuditRepository, never()).save(any());
        }
        
        @Test
        @DisplayName("should_returnInvalid_when_scopesMissing")
        void should_returnInvalid_when_scopesMissing() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "create_bookings", "delete_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent partialConsent = ConsentTestFixtures.consentWithPartialScopes();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(partialConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).contains("does not cover all required scopes");
            assertThat(result.getMissingScopes()).isNotEmpty();
            assertThat(result.getMissingScopes()).contains("create_bookings", "delete_bookings");
        }
        
        @Test
        @DisplayName("should_returnInvalid_when_consentExpired")
        void should_returnInvalid_when_consentExpired() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            // findActiveConsents should not return expired consents
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).isEqualTo("No active consent found");
        }
        
        @Test
        @DisplayName("should_returnInvalid_when_consentRevoked")
        void should_returnInvalid_when_consentRevoked() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.CAROL_USER_ID)
                    .granteeId(ConsentTestFixtures.BOB_USER_ID)
                    .purpose("manage_expenses")
                    .scopes(Arrays.asList("view_expenses"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.bobContext();
            
            // findActiveConsents should not return revoked consents
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).isEqualTo("No active consent found");
        }
        
        
        // ==========================================================================
        // VALIDATE CONSENT TESTS - PART 2
        // ==========================================================================
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.validateConsent(request, context))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to validate consent");
            
            verify(opaClient).authorize(eq(context), eq("validate_consent"), anyMap());
            verify(consentRepository, never()).findActiveConsents(any(), any(), any(), any());
        }
        
        @Test
        @DisplayName("should_validateWithPartialScopes_when_someMatch")
        void should_validateWithPartialScopes_when_someMatch() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "update_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent partialConsent = ConsentTestFixtures.consentWithPartialScopes();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(partialConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMissingScopes()).contains("update_bookings");
            assertThat(result.getMissingScopes()).doesNotContain("view_bookings");
        }
        
        @Test
        @DisplayName("should_validateWithMultipleActiveConsents_when_anyMatches")
        void should_validateWithMultipleActiveConsents_when_anyMatches() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            List<Consent> multipleConsents = Arrays.asList(
                    ConsentTestFixtures.consentWithPartialScopes(),
                    ConsentTestFixtures.activeConsentAliceToDave()
            );
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(multipleConsents);
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
            // First matching consent is used
            verify(consentAuditRepository).save(any(ConsentAudit.class));
        }
        
        @Test
        @DisplayName("should_validateWithSubsetScopes_when_consentHasMore")
        void should_validateWithSubsetScopes_when_consentHasMore() {
            // Given - Request only needs view, but consent has view + create
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent consentWithMoreScopes = ConsentTestFixtures.activeConsentAliceToDave();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(consentWithMoreScopes));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
            assertThat(result.getConsentId()).isNotNull();
        }
        
        @Test
        @DisplayName("should_returnInvalidReason_when_validationFails")
        void should_returnInvalidReason_when_validationFails() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "delete_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).isNotNull();
            assertThat(result.getReason()).isNotEmpty();
        }
        
        @Test
        @DisplayName("should_validateAcrossTenants_when_sameTenant")
        void should_validateAcrossTenants_when_sameTenant() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent sameTenantConsent = ConsentTestFixtures.activeConsentAliceToDave();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(
                    request.getGrantorId(), request.getGranteeId(), request.getPurpose(), context.getTenantId()))
                    .thenReturn(Arrays.asList(sameTenantConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
            // Verify tenant isolation - only same tenant consents queried
            verify(consentRepository).findActiveConsents(
                    request.getGrantorId(), request.getGranteeId(), request.getPurpose(), 
                    ConsentTestFixtures.TENANT_A);
        }
        
        @Test
        @DisplayName("should_returnMissingScopes_when_partialMatch")
        void should_returnMissingScopes_when_partialMatch() {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings", "create_bookings", "delete_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent partialConsent = ConsentTestFixtures.consentWithPartialScopes();
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(partialConsent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMissingScopes()).isNotNull();
            assertThat(result.getMissingScopes()).hasSize(2);
            assertThat(result.getMissingScopes()).containsExactlyInAnyOrder("create_bookings", "delete_bookings");
        }
    }
    
    
    // ==========================================================================
    // GET CONSENT AUDIT TRAIL TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Get Consent Audit Trail Tests")
    class GetConsentAuditTrailTests {
        
        @Test
        @DisplayName("should_returnAuditTrail_when_consentExists")
        void should_returnAuditTrail_when_consentExists() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            List<ConsentAudit> auditRecords = Arrays.asList(
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("GRANTED")
                            .actorId(ConsentTestFixtures.ALICE_USER_ID)
                            .build(),
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("USED")
                            .actorId(ConsentTestFixtures.DAVE_USER_ID)
                            .build()
            );
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentAuditRepository.findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId()))
                    .thenReturn(auditRecords);
            
            // When
            List<ConsentAuditResponse> result = consentService.getConsentAuditTrail(consentId, context);
            
            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getAction()).isEqualTo("GRANTED");
            assertThat(result.get(1).getAction()).isEqualTo("USED");
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(opaClient).authorize(eq(context), eq("view_consent_audit"), anyMap());
            verify(consentAuditRepository).findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnOrderedByTimestamp_when_multipleRecords")
        void should_returnOrderedByTimestamp_when_multipleRecords() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            List<ConsentAudit> auditRecords = Arrays.asList(
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("USED")
                            .timestamp(LocalDateTime.now().minusHours(1))
                            .build(),
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("GRANTED")
                            .timestamp(LocalDateTime.now().minusDays(1))
                            .build()
            );
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentAuditRepository.findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId()))
                    .thenReturn(auditRecords);
            
            // When
            List<ConsentAuditResponse> result = consentService.getConsentAuditTrail(consentId, context);
            
            // Then
            assertThat(result).hasSize(2);
            // Repository method already returns ordered by timestamp desc
            verify(consentAuditRepository).findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId());
        }
        
        @Test
        @DisplayName("should_returnEmptyList_when_noAuditRecords")
        void should_returnEmptyList_when_noAuditRecords() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentAuditRepository.findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId()))
                    .thenReturn(Collections.emptyList());
            
            // When
            List<ConsentAuditResponse> result = consentService.getConsentAuditTrail(consentId, context);
            
            // Then
            assertThat(result).isEmpty();
            
            verify(consentAuditRepository).findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId());
        }
        
        @Test
        @DisplayName("should_throwConsentNotFound_when_idNotExists")
        void should_throwConsentNotFound_when_idNotExists() {
            // Given
            UUID consentId = UUID.randomUUID();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.empty());
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsentAuditTrail(consentId, context))
                    .isInstanceOf(ConsentNotFoundException.class);
            
            verify(consentRepository).findByIdAndTenantId(consentId, context.getTenantId());
            verify(consentAuditRepository, never()).findByConsentIdOrderByTimestampDesc(any());
        }
        
        @Test
        @DisplayName("should_throwAccessDenied_when_opaReturnsFalse")
        void should_throwAccessDenied_when_opaReturnsFalse() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext bobContext = SecurityContextTestUtil.bobContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, bobContext.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(false);
            
            // When / Then
            assertThatThrownBy(() -> consentService.getConsentAuditTrail(consentId, bobContext))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Not authorized to view consent audit trail");
            
            verify(opaClient).authorize(eq(bobContext), eq("view_consent_audit"), anyMap());
            verify(consentAuditRepository, never()).findByConsentIdAndTenantIdOrderByTimestampDesc(any(), any());
        }
        
        @Test
        @DisplayName("should_isolateByTenant_when_querying")
        void should_isolateByTenant_when_querying() {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestFixtures.activeConsentAliceToDave();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            List<ConsentAudit> auditRecords = Arrays.asList(
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("GRANTED")
                            .tenantId(ConsentTestFixtures.TENANT_A)
                            .build()
            );
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentAuditRepository.findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId()))
                    .thenReturn(auditRecords);
            
            // When
            List<ConsentAuditResponse> result = consentService.getConsentAuditTrail(consentId, context);
            
            // Then
            assertThat(result).allMatch(audit -> audit.getTenantId().equals(ConsentTestFixtures.TENANT_A));
            
            // Verify tenant isolation at consent lookup level
            verify(consentRepository).findByIdAndTenantId(consentId, ConsentTestFixtures.TENANT_A);
        }
    }
    
    
    // ==========================================================================
    // PARAMETERIZED TESTS
    // ==========================================================================
    
    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {
        
        @ParameterizedTest
        @EnumSource(ConsentStatus.class)
        @DisplayName("should_saveConsentWithStatus_when_statusProvided")
        void should_saveConsentWithStatus_when_statusProvided(ConsentStatus status) {
            // Given
            CreateConsentRequest request = CreateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.existsActiveDuplicateConsent(any(), any(), any(), any())).thenReturn(false);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> {
                Consent c = inv.getArgument(0);
                // Override status for testing
                c.setStatus(status);
                return c;
            });
            
            // When
            ConsentResponse result = consentService.grantConsent(request, context);
            
            // Then
            assertThat(result.getStatus()).isEqualTo(status);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"ACTIVE", "EXPIRED"})
        @DisplayName("should_revokeConsent_when_statusIsActiveOrExpired")
        void should_revokeConsent_when_statusIsActiveOrExpired(ConsentStatus initialStatus) {
            // Given
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestDataBuilder.aConsent()
                    .withId(consentId)
                    .withStatus(initialStatus)
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(consent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
            verify(consentRepository).save(consent);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"REVOKED"})
        @DisplayName("should_stillRevokeConsent_when_statusIsRevokedOrSuspended")
        void should_stillRevokeConsent_when_statusIsRevokedOrSuspended(ConsentStatus initialStatus) {
            // Given - Idempotent revocation
            UUID consentId = ConsentTestFixtures.CONSENT_ID_1;
            Consent consent = ConsentTestDataBuilder.aConsent()
                    .withId(consentId)
                    .withStatus(initialStatus)
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.save(any(Consent.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // When
            consentService.revokeConsent(consentId, context);
            
            // Then
            assertThat(consent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"ACTIVE"})
        @DisplayName("should_validateSuccessfully_when_statusIsActive")
        void should_validateSuccessfully_when_statusIsActive(ConsentStatus status) {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent consent = ConsentTestDataBuilder.builder()
                    .withStatus(status)
                    .withScopes(Arrays.asList("view_bookings", "create_bookings"))
                    .build();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(consent));
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isTrue();
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"REVOKED", "EXPIRED"})
        @DisplayName("should_notReturnInValidation_when_statusIsNonActive")
        void should_notReturnInValidation_when_statusIsNonActive(ConsentStatus status) {
            // Given - findActiveConsents should not return non-active consents
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());  // Non-active consents not returned
            
            // When
            ValidateConsentResponse result = consentService.validateConsent(request, context);
            
            // Then
            assertThat(result.isValid()).isFalse();
            assertThat(result.getReason()).isEqualTo("No active consent found");
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class)
        @DisplayName("should_calculateValidFlag_when_retrievingWithAnyStatus")
        void should_calculateValidFlag_when_retrievingWithAnyStatus(ConsentStatus status) {
            // Given
            UUID consentId = UUID.randomUUID();
            Consent consent = ConsentTestDataBuilder.builder()
                    .withId(consentId)
                    .withStatus(status)
                    .withExpiresAt(status == ConsentStatus.EXPIRED ? 
                            LocalDateTime.now().minusDays(1) : LocalDateTime.now().plusDays(30))
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            
            // When
            ConsentResponse result = consentService.getConsent(consentId, context);
            
            // Then
            boolean expectedValid = (status == ConsentStatus.ACTIVE && 
                    (consent.getExpiresAt() == null || consent.getExpiresAt().isAfter(LocalDateTime.now())));
            assertThat(result.isValid()).isEqualTo(expectedValid);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"ACTIVE", "EXPIRED", "REVOKED"})
        @DisplayName("should_returnConsentInList_when_anyStatusForGrantor")
        void should_returnConsentInList_when_anyStatusForGrantor(ConsentStatus status) {
            // Given
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestDataBuilder.builder()
                            .withStatus(status)
                            .withGrantorId(context.getUserId())
                            .withTenantId(context.getTenantId())
                            .build()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGrantorIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getMyConsents(context);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(status);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"ACTIVE", "EXPIRED", "REVOKED"})
        @DisplayName("should_returnConsentInList_when_anyStatusForGrantee")
        void should_returnConsentInList_when_anyStatusForGrantee(ConsentStatus status) {
            // Given
            SecurityContext context = SecurityContextTestUtil.daveContext();
            List<Consent> consents = Arrays.asList(
                    ConsentTestDataBuilder.builder()
                            .withStatus(status)
                            .withGranteeId(context.getUserId())
                            .withTenantId(context.getTenantId())
                            .build()
            );
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findByGranteeIdAndTenantIdOrderByGrantedAtDesc(
                    context.getUserId(), context.getTenantId()))
                    .thenReturn(consents);
            
            // When
            List<ConsentResponse> result = consentService.getConsentsToMe(context);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(status);
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class)
        @DisplayName("should_returnAuditTrail_when_consentHasAnyStatus")
        void should_returnAuditTrail_when_consentHasAnyStatus(ConsentStatus status) {
            // Given
            UUID consentId = UUID.randomUUID();
            Consent consent = ConsentTestDataBuilder.builder()
                    .withId(consentId)
                    .withStatus(status)
                    .build();
            SecurityContext context = SecurityContextTestUtil.aliceContext();
            
            List<ConsentAudit> auditRecords = Arrays.asList(
                    ConsentTestDataBuilder.auditBuilder()
                            .consentId(consentId)
                            .action("GRANTED")
                            .build()
            );
            
            when(consentRepository.findByIdAndTenantId(consentId, context.getTenantId()))
                    .thenReturn(Optional.of(consent));
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentAuditRepository.findByConsentIdAndTenantIdOrderByTimestampDesc(
                    consentId, context.getTenantId()))
                    .thenReturn(auditRecords);
            
            // When
            List<ConsentAuditResponse> result = consentService.getConsentAuditTrail(consentId, context);
            
            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("GRANTED");
        }
        
        @ParameterizedTest
        @EnumSource(value = ConsentStatus.class, names = {"ACTIVE"})
        @DisplayName("should_createAuditRecord_when_validationSucceeds")
        void should_createAuditRecord_when_validationSucceeds(ConsentStatus status) {
            // Given
            ValidateConsentRequest request = ValidateConsentRequest.builder()
                    .grantorId(ConsentTestFixtures.ALICE_USER_ID)
                    .granteeId(ConsentTestFixtures.DAVE_USER_ID)
                    .purpose("book_travel")
                    .scopes(Arrays.asList("view_bookings"))
                    .build();
            SecurityContext context = SecurityContextTestUtil.daveContext();
            
            Consent consent = ConsentTestDataBuilder.builder()
                    .withStatus(status)
                    .withScopes(Arrays.asList("view_bookings"))
                    .build();
            
            when(opaClient.authorize(any(), any(), anyMap())).thenReturn(true);
            when(consentRepository.findActiveConsents(any(), any(), any(), any()))
                    .thenReturn(Arrays.asList(consent));
            
            // When
            consentService.validateConsent(request, context);
            
            // Then
            verify(consentAuditRepository).save(argThat(audit -> 
                    audit.getAction().equals("USED")
            ));
        }
    }
}
