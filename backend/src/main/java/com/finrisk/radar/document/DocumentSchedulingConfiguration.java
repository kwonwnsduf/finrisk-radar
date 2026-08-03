package com.finrisk.radar.document;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "app.worker",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DocumentSchedulingConfiguration {}
