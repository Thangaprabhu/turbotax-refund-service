package com.turbotax.ai.service;

import com.turbotax.ai.domain.dto.response.GuidanceDoc;

import java.util.List;

/** Turns the retrieved guidance docs for a situation into one narrative paragraph. */
public interface NarrativeSynthesizer {
    String synthesize(List<GuidanceDoc> docs);
}
