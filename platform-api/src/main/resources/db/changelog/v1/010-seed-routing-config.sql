--liquibase formatted sql

--changeset platform:010-seed-routing-config

-- Demo routing config for tenant-1 / SETTLEMENT_EXCEPTION. Ingestion no longer has a hardcoded
-- fallback group: every tenant and workflow type that ingests records needs a ROUTING_CONFIG, and
-- "no rule matched" is answered by this document's own defaultGroup. Group ids match the demo data.
INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active) VALUES (
    'cfg-routing-settlement-v1',
    'tenant-1',
    'SETTLEMENT_EXCEPTION',
    'ROUTING_CONFIG',
    '{
        "id": "cfg-routing-settlement-v1",
        "tenantId": "tenant-1",
        "workflowType": "SETTLEMENT_EXCEPTION",
        "defaultGroup": "group-ops",
        "alertOnDefault": false,
        "rules": [
            {
                "id": "rule-high-notional",
                "name": "High notional exceptions go to the senior group",
                "description": "Notional of 10,000,000 or more in the trade currency is escalated to senior review.",
                "priority": 1,
                "conditions": {
                    "type": "LEAF",
                    "field": "trade.notionalAmount.amount",
                    "operator": "GTE",
                    "value": "10000000"
                },
                "targetGroup": "group-senior",
                "active": true,
                "effectiveFrom": null,
                "effectiveTo": null
            }
        ],
        "active": true,
        "version": 1,
        "createdAt": "2026-11-08T00:00:00Z",
        "updatedAt": "2026-11-08T00:00:00Z",
        "createdBy": "system"
    }',
    '1',
    true
);

--rollback DELETE FROM config_documents WHERE id = 'cfg-routing-settlement-v1';
