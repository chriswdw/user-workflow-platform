package com.platform.api.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.config.domain.model.DraftConfigs;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviseSubmissionRequestTest {

  @Test
  void nullDraftConfigsDefaultsToAnEmptyDraftConfigs() {
    ReviseSubmissionRequest request = new ReviseSubmissionRequest(null);

    assertThat(request.draftConfigs())
        .isEqualTo(new DraftConfigs(null, null, null, null, null, null));
  }

  @Test
  void providedDraftConfigsIsUsedAsIs() {
    DraftConfigs draftConfigs = new DraftConfigs(null, null, null, null, Map.of("a", 1), null);

    ReviseSubmissionRequest request = new ReviseSubmissionRequest(draftConfigs);

    assertThat(request.draftConfigs()).isEqualTo(draftConfigs);
  }
}
