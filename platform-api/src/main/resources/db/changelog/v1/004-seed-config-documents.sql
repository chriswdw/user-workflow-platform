--liquibase formatted sql

--changeset platform:004-seed-config-documents

-- SETTLEMENT_EXCEPTION detail-view config
INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active) VALUES (
    'cfg-dvc-settlement-v1',
    'tenant-1',
    'SETTLEMENT_EXCEPTION',
    'DETAIL_VIEW_CONFIG',
    '{
        "id": "cfg-dvc-settlement-v1",
        "tenantId": "tenant-1",
        "workflowType": "SETTLEMENT_EXCEPTION",
        "active": true,
        "version": 1,
        "sections": [
            {
                "title": "Trade Details",
                "layout": "TWO_COLUMN",
                "fields": [
                    {"field": "trade.ref",                     "label": "Trade Ref",   "formatter": "TEXT"},
                    {"field": "trade.valueDate",               "label": "Value Date",  "formatter": "DATE"},
                    {"field": "trade.notionalAmount.amount",   "label": "Notional",    "formatter": "CURRENCY"},
                    {"field": "trade.notionalAmount.currency", "label": "Currency",    "formatter": "TEXT"},
                    {"field": "status",                        "label": "Status",      "formatter": "BADGE"},
                    {"field": "priorityLevel",                 "label": "Priority",    "formatter": "BADGE"}
                ]
            },
            {
                "title": "Counterparty",
                "layout": "TWO_COLUMN",
                "fields": [
                    {"field": "counterparty.name", "label": "Name", "formatter": "TEXT"},
                    {"field": "counterparty.lei",  "label": "LEI",  "formatter": "TEXT"}
                ]
            },
            {
                "title": "Assignment",
                "layout": "TWO_COLUMN",
                "collapsible": true,
                "fields": [
                    {"field": "assignedGroup", "label": "Group",   "formatter": "TEXT"},
                    {"field": "source",        "label": "Source",  "formatter": "TEXT"},
                    {"field": "makerUserId",   "label": "Maker",   "formatter": "TEXT"},
                    {"field": "createdAt",     "label": "Created", "formatter": "DATETIME"}
                ]
            }
        ],
        "actions": [
            {
                "transition": "close-as-resolved",
                "label": "Close as Resolved",
                "style": "PRIMARY",
                "visibleInStates": ["UNDER_REVIEW", "ESCALATED"],
                "visibleRoles": ["ANALYST", "SUPERVISOR"],
                "confirmationRequired": true,
                "confirmationMessage": "Close this exception as resolved?",
                "inputFields": [
                    {"field": "resolution.reason", "label": "Reason", "inputType": "TEXTAREA", "required": true}
                ]
            },
            {
                "transition": "escalate",
                "label": "Escalate",
                "style": "SECONDARY",
                "visibleInStates": ["UNDER_REVIEW"],
                "visibleRoles": ["ANALYST", "SUPERVISOR"]
            },
            {
                "transition": "assign-to-compliance",
                "label": "Compliance Review",
                "style": "SECONDARY",
                "visibleInStates": ["UNDER_REVIEW", "ESCALATED"],
                "visibleRoles": ["SUPERVISOR"]
            },
            {
                "transition": "return-to-review",
                "label": "Return to Review",
                "style": "SECONDARY",
                "visibleInStates": ["ESCALATED"],
                "visibleRoles": ["SUPERVISOR"]
            }
        ]
    }',
    '1',
    true
);

-- SETTLEMENT_EXCEPTION workflow config
INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active) VALUES (
    'cfg-wf-settlement-v1',
    'tenant-1',
    'SETTLEMENT_EXCEPTION',
    'WORKFLOW_CONFIG',
    '{
        "id": "cfg-wf-settlement-v1",
        "tenantId": "tenant-1",
        "workflowType": "SETTLEMENT_EXCEPTION",
        "initialState": "UNDER_REVIEW",
        "active": true,
        "states": [
            {"name": "UNDER_REVIEW",      "terminal": false, "allowedRoles": ["ANALYST", "SUPERVISOR"]},
            {"name": "ESCALATED",         "terminal": false, "allowedRoles": ["ANALYST", "SUPERVISOR"]},
            {"name": "COMPLIANCE_REVIEW", "terminal": false, "allowedRoles": ["SUPERVISOR"]},
            {"name": "CLOSED",            "terminal": true,  "allowedRoles": ["ANALYST", "SUPERVISOR"]}
        ],
        "transitions": [
            {
                "name": "close-as-resolved",
                "fromState": "UNDER_REVIEW",
                "toState": "CLOSED",
                "trigger": "USER_ACTION",
                "allowedRoles": ["ANALYST", "SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            },
            {
                "name": "escalate",
                "fromState": "UNDER_REVIEW",
                "toState": "ESCALATED",
                "trigger": "USER_ACTION",
                "allowedRoles": ["ANALYST", "SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            },
            {
                "name": "assign-to-compliance",
                "fromState": "UNDER_REVIEW",
                "toState": "COMPLIANCE_REVIEW",
                "trigger": "USER_ACTION",
                "allowedRoles": ["SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            },
            {
                "name": "assign-to-compliance",
                "fromState": "ESCALATED",
                "toState": "COMPLIANCE_REVIEW",
                "trigger": "USER_ACTION",
                "allowedRoles": ["SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            },
            {
                "name": "return-to-review",
                "fromState": "ESCALATED",
                "toState": "UNDER_REVIEW",
                "trigger": "USER_ACTION",
                "allowedRoles": ["SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            },
            {
                "name": "close-as-resolved",
                "fromState": "ESCALATED",
                "toState": "CLOSED",
                "trigger": "USER_ACTION",
                "allowedRoles": ["ANALYST", "SUPERVISOR"],
                "requiresMakerChecker": false,
                "actions": [],
                "validationRules": []
            }
        ]
    }',
    '1',
    true
);

--rollback DELETE FROM config_documents WHERE id IN ('cfg-dvc-settlement-v1', 'cfg-wf-settlement-v1');
