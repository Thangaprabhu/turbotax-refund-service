package com.turbotax.refund.mapper;

import com.turbotax.refund.domain.dto.response.FilingResponse;
import com.turbotax.refund.dynamodb.FilingItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FilingMapper {

    FilingResponse toResponse(FilingItem item);
}
