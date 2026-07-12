package com.turbotax.refund.unit;

import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.entity.Taxpayer;
import com.turbotax.refund.domain.entity.User;
import com.turbotax.refund.domain.entity.UserTaxpayerAccess;
import com.turbotax.refund.domain.enums.TaxpayerType;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.mapper.TaxpayerMapper;
import com.turbotax.refund.metrics.TaxMetrics;
import com.turbotax.refund.repository.TaxpayerRepository;
import com.turbotax.refund.repository.UserRepository;
import com.turbotax.refund.repository.UserTaxpayerAccessRepository;
import com.turbotax.refund.service.PiiEncryptionService;
import com.turbotax.refund.service.TaxpayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxpayerServiceTest {

    @Mock TaxpayerRepository taxpayerRepository;
    @Mock UserRepository userRepository;
    @Mock UserTaxpayerAccessRepository accessRepository;
    @Mock PiiEncryptionService piiEncryptionService;
    @Mock TaxMetrics taxMetrics;
    @Mock TaxpayerMapper taxpayerMapper;

    @InjectMocks TaxpayerService taxpayerService;

    private TaxpayerResponse responseFor(Taxpayer t) {
        return new TaxpayerResponse(t.getId(), t.getTaxpayerType(), t.getDisplayName(), t.getEntityType(), t.getStateOfReg(), t.getCreatedAt());
    }

    @Test
    void create_shouldEncryptTaxIdAndGrantOwnerAccess() {
        var userId = UUID.randomUUID();
        var request = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "123-45-6789", "John Doe", null, null);

        var user = new User();
        user.setId(userId);

        var entityToSave = new Taxpayer();
        entityToSave.setDisplayName("John Doe");
        entityToSave.setTaxpayerType(TaxpayerType.INDIVIDUAL);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(piiEncryptionService.encrypt("123-45-6789")).thenReturn("encrypted-ssn");
        when(piiEncryptionService.hash("123-45-6789")).thenReturn("hashed-ssn");
        when(taxpayerRepository.existsByTaxIdHash("hashed-ssn")).thenReturn(false);
        when(taxpayerMapper.toEntity(request)).thenReturn(entityToSave);
        when(taxpayerRepository.save(any())).thenAnswer(inv -> {
            Taxpayer t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(accessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taxpayerMapper.toResponse(any(Taxpayer.class))).thenAnswer(inv -> responseFor(inv.getArgument(0)));

        var response = taxpayerService.create(userId, request);

        assertThat(response.displayName()).isEqualTo("John Doe");
        assertThat(response.taxpayerType()).isEqualTo(TaxpayerType.INDIVIDUAL);
        verify(accessRepository).save(any(UserTaxpayerAccess.class));
    }

    @Test
    void create_shouldThrowConflict_whenTaxIdAlreadyExists() {
        var userId = UUID.randomUUID();
        var request = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "123-45-6789", "John Doe", null, null);

        when(piiEncryptionService.hash("123-45-6789")).thenReturn("hashed-ssn");
        when(taxpayerRepository.existsByTaxIdHash("hashed-ssn")).thenReturn(true);

        assertThatThrownBy(() -> taxpayerService.create(userId, request))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void create_shouldThrowNotFound_whenUserMissing() {
        var userId = UUID.randomUUID();
        var request = new CreateTaxpayerRequest(TaxpayerType.INDIVIDUAL, "123-45-6789", "John Doe", null, null);

        when(piiEncryptionService.hash("123-45-6789")).thenReturn("hashed-ssn");
        when(taxpayerRepository.existsByTaxIdHash("hashed-ssn")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxpayerService.create(userId, request))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void findAllForUser_shouldReturnPagedResponse() {
        var userId = UUID.randomUUID();
        var taxpayer = new Taxpayer();
        taxpayer.setId(UUID.randomUUID());
        taxpayer.setDisplayName("Jane Doe");
        taxpayer.setTaxpayerType(TaxpayerType.INDIVIDUAL);

        var access = new UserTaxpayerAccess();
        access.setTaxpayer(taxpayer);

        Page<UserTaxpayerAccess> page = new PageImpl<>(List.of(access), Pageable.ofSize(10), 1);
        when(accessRepository.findByUser_IdAndActiveTrue(org.mockito.ArgumentMatchers.eq(userId), any()))
            .thenReturn(page);
        when(taxpayerMapper.toResponse(taxpayer)).thenReturn(responseFor(taxpayer));

        PageResponse<TaxpayerResponse> result = taxpayerService.findAllForUser(userId, 0, 10);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).displayName()).isEqualTo("Jane Doe");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void findById_shouldReturnTaxpayer_whenAccessGranted() {
        var userId = UUID.randomUUID();
        var taxpayerId = UUID.randomUUID();
        var taxpayer = new Taxpayer();
        taxpayer.setId(taxpayerId);
        taxpayer.setDisplayName("Acme LLC");

        when(accessRepository.existsByUser_IdAndTaxpayer_IdAndActiveTrue(userId, taxpayerId)).thenReturn(true);
        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.of(taxpayer));
        when(taxpayerMapper.toResponse(taxpayer)).thenReturn(responseFor(taxpayer));

        var response = taxpayerService.findById(userId, taxpayerId);

        assertThat(response.displayName()).isEqualTo("Acme LLC");
    }

    @Test
    void findById_shouldThrowNotFound_whenTaxpayerMissing() {
        var userId = UUID.randomUUID();
        var taxpayerId = UUID.randomUUID();

        when(accessRepository.existsByUser_IdAndTaxpayer_IdAndActiveTrue(userId, taxpayerId)).thenReturn(true);
        when(taxpayerRepository.findById(taxpayerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxpayerService.findById(userId, taxpayerId))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void findById_shouldThrowForbidden_whenUserHasNoAccess() {
        var userId = UUID.randomUUID();
        var taxpayerId = UUID.randomUUID();

        when(accessRepository.existsByUser_IdAndTaxpayer_IdAndActiveTrue(userId, taxpayerId)).thenReturn(false);

        assertThatThrownBy(() -> taxpayerService.findById(userId, taxpayerId))
            .isInstanceOf(TaxRefundException.class);
    }

    @Test
    void assertAccess_shouldPass_whenAccessGranted() {
        var userId = UUID.randomUUID();
        var taxpayerId = UUID.randomUUID();

        when(accessRepository.existsByUser_IdAndTaxpayer_IdAndActiveTrue(userId, taxpayerId)).thenReturn(true);

        taxpayerService.assertAccess(userId, taxpayerId);
    }
}
