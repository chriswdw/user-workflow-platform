--liquibase formatted sql

--changeset platform:007-submission-statuses
CREATE TABLE submission_statuses (
    code         VARCHAR(30)  NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    description  TEXT
);

INSERT INTO submission_statuses (code, display_name, description) VALUES
    ('DRAFT',            'Draft',            'Being configured by the submitter'),
    ('PENDING_APPROVAL', 'Pending Approval',  'Awaiting review by a second approver'),
    ('APPROVED',         'Approved',          'Configuration is live'),
    ('REJECTED',         'Rejected',          'Declined by the reviewer');

--rollback DROP TABLE submission_statuses;

--changeset platform:007-workflow-type-submissions
CREATE TABLE workflow_type_submissions (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    workflow_type    VARCHAR(100) NOT NULL,
    display_name     VARCHAR(255) NOT NULL,
    description      TEXT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                         REFERENCES submission_statuses(code),
    draft_configs    JSONB        NOT NULL DEFAULT '{}',
    submitted_by     VARCHAR(255) NOT NULL,
    submitted_at     TIMESTAMPTZ,
    reviewed_by      VARCHAR(255),
    reviewed_at      TIMESTAMPTZ,
    rejection_reason TEXT,
    current_step     INTEGER      NOT NULL DEFAULT 1,
    version          INTEGER      NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_submissions_tenant_status
    ON workflow_type_submissions (tenant_id, status);

CREATE INDEX idx_submissions_submitted_by
    ON workflow_type_submissions (tenant_id, submitted_by, status);

CREATE UNIQUE INDEX idx_submissions_tenant_workflow_type
    ON workflow_type_submissions (tenant_id, workflow_type)
    WHERE status NOT IN ('REJECTED');

--rollback DROP TABLE workflow_type_submissions;
