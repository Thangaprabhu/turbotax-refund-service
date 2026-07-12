package com.turbotax.refund.service;

import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.response.PageResponse;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.entity.Taxpayer;
import com.turbotax.refund.domain.entity.UserTaxpayerAccess;
import com.turbotax.refund.domain.enums.AccessRole;
import com.turbotax.refund.exception.TaxRefundException;
import com.turbotax.refund.mapper.TaxpayerMapper;
import com.turbotax.refund.metrics.TaxMetrics;
import com.turbotax.refund.repository.TaxpayerRepository;
import com.turbotax.refund.repository.UserRepository;
import com.turbotax.refund.repository.UserTaxpayerAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxpayerService {

    private final TaxpayerRepository taxpayerRepository;
    private final UserRepository userRepository;
    private final UserTaxpayerAccessRepository accessRepository;
    private final PiiEncryptionService piiEncryptionService;
    private final TaxMetrics taxMetrics;
    private final TaxpayerMapper taxpayerMapper;

    @Transactional
    public TaxpayerResponse create(UUID userId, CreateTaxpayerRequest request) {
        String taxIdHash = piiEncryptionService.hash(request.taxId());

        if (taxpayerRepository.existsByTaxIdHash(taxIdHash)) {
            throw TaxRefundException.conflict("Taxpayer already registered");
        }
        var user = userRepository.findById(userId)
            .orElseThrow(() -> TaxRefundException.notFound("User not found"));

        Taxpayer taxpayer = taxpayerMapper.toEntity(request);
        taxpayer.setTaxIdEncrypted(piiEncryptionService.encrypt(request.taxId()));
        taxpayer.setTaxIdHash(taxIdHash);
        Taxpayer saved = taxpayerRepository.save(taxpayer);

        // Grant OWNER access to the creating user
        UserTaxpayerAccess access = new UserTaxpayerAccess();
        access.setUser(user);
        access.setTaxpayer(saved);
        access.setRole(AccessRole.OWNER);
        access.setGrantedBy(userId);
        accessRepository.save(access);

        log.info("Taxpayer created: {} by user: {}", saved.getId(), userId);
        return taxpayerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxpayerResponse> findAllForUser(UUID userId, int page, int size) {
        Page<UserTaxpayerAccess> result = accessRepository.findByUser_IdAndActiveTrue(
            userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "grantedAt"))
        );
        List<TaxpayerResponse> content = result.getContent().stream()
            .map(a -> taxpayerMapper.toResponse(a.getTaxpayer()))
            .toList();
        return PageResponse.of(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TaxpayerResponse findById(UUID userId, UUID taxpayerId) {
        assertAccess(userId, taxpayerId);
        Taxpayer taxpayer = taxpayerRepository.findById(taxpayerId)
            .orElseThrow(() -> TaxRefundException.notFound("Taxpayer not found"));
        return taxpayerMapper.toResponse(taxpayer);
    }

    public void assertAccess(UUID userId, UUID taxpayerId) {
        if (!accessRepository.existsByUser_IdAndTaxpayer_IdAndActiveTrue(userId, taxpayerId)) {
            throw TaxRefundException.forbidden("Access denied to taxpayer: " + taxpayerId);
        }
    }
}
