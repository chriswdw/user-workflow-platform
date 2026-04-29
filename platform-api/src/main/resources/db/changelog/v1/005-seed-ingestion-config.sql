--liquibase formatted sql

--changeset platform:005-seed-ingestion-config

INSERT INTO config_documents (id, tenant_id, workflow_type, config_type, content, version, active) VALUES (
    'cfg-ing-settlement-kafka-v1',
    'tenant-1',
    'SETTLEMENT_EXCEPTION',
    'INGESTION_SOURCE_CONFIG',
    '{
        "tenantId": "tenant-1",
        "workflowType": "SETTLEMENT_EXCEPTION",
        "sourceType": "KAFKA",
        "fieldMappings": [
            {"sourceField": "tradeRef",          "targetField": "trade.ref",                     "required": true},
            {"sourceField": "valueDate",          "targetField": "trade.valueDate",               "required": false},
            {"sourceField": "notionalAmount",     "targetField": "trade.notionalAmount.amount",   "required": false},
            {"sourceField": "currency",           "targetField": "trade.notionalAmount.currency", "required": false},
            {"sourceField": "counterpartyName",   "targetField": "counterparty.name",             "required": false},
            {"sourceField": "counterpartyLei",    "targetField": "counterparty.lei",              "required": false}
        ],
        "unknownColumnPolicy": "IGNORE",
        "idempotencyKeyStrategy": "EXPLICIT_FIELD",
        "idempotencyKeyFields": [],
        "idempotencyExplicitField": "tradeRef",
        "initialState": "UNDER_REVIEW"
    }',
    '1',
    true
);

--rollback DELETE FROM config_documents WHERE id = 'cfg-ing-settlement-kafka-v1';
