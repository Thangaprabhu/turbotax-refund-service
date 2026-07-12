package com.turbotax.refund.mapper;

import com.turbotax.refund.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.refund.domain.dto.response.TaxpayerResponse;
import com.turbotax.refund.domain.entity.Taxpayer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaxpayerMapper {

    TaxpayerResponse toResponse(Taxpayer taxpayer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taxIdEncrypted", ignore = true)
    @Mapping(target = "taxIdHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "accesses", ignore = true)
    Taxpayer toEntity(CreateTaxpayerRequest request);
}
