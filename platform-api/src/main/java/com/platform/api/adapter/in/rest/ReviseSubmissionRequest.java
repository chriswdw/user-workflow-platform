package com.platform.api.adapter.in.rest;

import com.platform.config.domain.model.DraftConfigs;

record ReviseSubmissionRequest(DraftConfigs draftConfigs) {
  ReviseSubmissionRequest {
    if (draftConfigs == null) draftConfigs = new DraftConfigs(null, null, null, null, null, null);
  }
}
