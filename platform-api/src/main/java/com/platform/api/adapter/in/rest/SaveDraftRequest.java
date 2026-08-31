package com.platform.api.adapter.in.rest;

import com.platform.config.domain.model.DraftConfigs;

record SaveDraftRequest(
        DraftConfigs draftConfigs,
        int currentStep
) {
    SaveDraftRequest {
        if (currentStep < 1) currentStep = 1;
        if (draftConfigs == null) draftConfigs = new DraftConfigs(null, null, null, null, null, null);
    }
}
