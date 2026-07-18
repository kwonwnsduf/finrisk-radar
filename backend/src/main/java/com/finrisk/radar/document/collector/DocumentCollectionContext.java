package com.finrisk.radar.document.collector;

import com.finrisk.radar.asset.Asset;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentCollectionContext(
    UUID jobId, Asset asset, String corpCode, LocalDate fromDate, LocalDate toDate) {}
