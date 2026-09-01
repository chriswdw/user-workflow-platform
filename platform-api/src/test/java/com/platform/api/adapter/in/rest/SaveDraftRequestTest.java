package com.platform.api.adapter.in.rest;

import com.platform.config.domain.model.DraftConfigs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SaveDraftRequestTest {

    @Test
    void currentStepBelowOneIsClampedToOne() {
        SaveDraftRequest request = new SaveDraftRequest(new DraftConfigs(null, null, null, null, null, null), 0);

        assertThat(request.currentStep()).isEqualTo(1);
    }

    @Test
    void currentStepAtOrAboveOneIsUnchanged() {
        SaveDraftRequest request = new SaveDraftRequest(new DraftConfigs(null, null, null, null, null, null), 3);

        assertThat(request.currentStep()).isEqualTo(3);
    }

    @Test
    void nullDraftConfigsDefaultsToAnEmptyDraftConfigs() {
        SaveDraftRequest request = new SaveDraftRequest(null, 1);

        assertThat(request.draftConfigs()).isEqualTo(new DraftConfigs(null, null, null, null, null, null));
    }

    @Test
    void providedDraftConfigsIsUsedAsIs() {
        DraftConfigs draftConfigs = new DraftConfigs(null, null, null, null, java.util.Map.of("a", 1), null);

        SaveDraftRequest request = new SaveDraftRequest(draftConfigs, 1);

        assertThat(request.draftConfigs()).isEqualTo(draftConfigs);
    }
}
