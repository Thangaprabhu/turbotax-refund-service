package com.turbotax.taxpayer.mapper;

import com.turbotax.taxpayer.domain.dto.request.CreateTaxpayerRequest;
import com.turbotax.taxpayer.domain.dto.response.TaxpayerResponse;
import com.turbotax.taxpayer.domain.entity.Taxpayer;
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
