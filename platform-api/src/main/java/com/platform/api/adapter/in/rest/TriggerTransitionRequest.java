package com.platform.api.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

record TriggerTransitionRequest(
        @NotBlank String transition,
        Map<String, Object> additionalFields
) {
    TriggerTransitionRequest {
        if (additionalFields == null) additionalFields = Map.of();
    }
}
