package com.platform.api.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.workflow.domain.model.OnFailure;
import com.platform.workflow.domain.model.TransitionAction;
import com.platform.workflow.domain.model.TransitionActionType;
import com.platform.workflow.domain.model.TransitionTrigger;
import com.platform.workflow.domain.model.ValidationRule;
import com.platform.workflow.domain.model.WorkflowConfig;
import com.platform.workflow.domain.model.WorkflowState;
import com.platform.workflow.domain.model.WorkflowTransition;
import com.platform.workflow.domain.ports.out.IWorkflowConfigRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WorkflowConfigJdbcRepository implements IWorkflowConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowConfigJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WorkflowConfig> findActiveByTenantAndWorkflowType(String tenantId, String workflowType) {
        String sql = """
                SELECT content FROM config_documents
                WHERE tenant_id = :tenantId
                  AND workflow_type = :workflowType
                  AND config_type  = 'WORKFLOW_CONFIG'
                  AND active       = true
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("workflowType", workflowType);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    private WorkflowConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> c = parseJson(rs.getString("content"));
        return new WorkflowConfig(
                (String) c.get("id"),
                (String) c.get("tenantId"),
                (String) c.get("workflowType"),
                (String) c.get("initialState"),
                parseStates(c),
                parseTransitions(c),
                Boolean.TRUE.equals(c.get("active"))
        );
    }

    @SuppressWarnings("unchecked")
    private static List<WorkflowState> parseStates(Map<String, Object> c) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) c.get("states");
        if (raw == null) return List.of();
        return raw.stream()
                .map(s -> new WorkflowState(
                        (String) s.get("name"),
                        Boolean.TRUE.equals(s.get("terminal")),
                        (List<String>) s.get("allowedRoles")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<WorkflowTransition> parseTransitions(Map<String, Object> c) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) c.get("transitions");
        if (raw == null) return List.of();
        return raw.stream()
                .map(t -> new WorkflowTransition(
                        (String) t.get("name"),
                        (String) t.get("fromState"),
                        (String) t.get("toState"),
                        TransitionTrigger.valueOf((String) t.get("trigger")),
                        (String) t.get("systemEventType"),
                        (List<String>) t.get("allowedRoles"),
                        Boolean.TRUE.equals(t.get("requiresMakerChecker")),
                        parseActions((List<Map<String, Object>>) t.get("actions")),
                        parseValidationRules((List<Map<String, Object>>) t.get("validationRules"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<TransitionAction> parseActions(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(a -> new TransitionAction(
                        TransitionActionType.valueOf((String) a.get("type")),
                        (Map<String, String>) a.get("config"),
                        a.get("onFailure") != null ? OnFailure.valueOf((String) a.get("onFailure")) : null))
                .toList();
    }

    private static List<ValidationRule> parseValidationRules(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .map(r -> new ValidationRule(
                        (String) r.get("field"),
                        (String) r.get("operator"),
                        (String) r.get("value")))
                .toList();
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise workflow config from JSONB", e);
        }
    }
}
