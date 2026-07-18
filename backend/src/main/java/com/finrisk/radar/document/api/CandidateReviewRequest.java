package com.finrisk.radar.document.api;

import jakarta.validation.constraints.Size;

public record CandidateReviewRequest(@Size(max = 1000) String reviewNote) {}
