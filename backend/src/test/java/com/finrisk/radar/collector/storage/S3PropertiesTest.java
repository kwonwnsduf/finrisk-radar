package com.finrisk.radar.collector.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class S3PropertiesTest {

  @Test
  void requiresOnlyRegionAndBucketWhenInstanceCredentialsAreUsed() {
    assertThat(new S3Properties("ap-northeast-2", "finrisk-raw-data").configured()).isTrue();
    assertThat(new S3Properties("", "finrisk-raw-data").configured()).isFalse();
    assertThat(new S3Properties("ap-northeast-2", "").configured()).isFalse();
  }
}
