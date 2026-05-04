Feature: Workflow type submission lifecycle

  Scenario: Business analyst creates a draft submission
    Given no submission exists for tenant "tenant-1" and workflow type "TRADE_BREAK"
    When user "alice" creates a submission for workflow type "TRADE_BREAK" with display name "Trade Break"
    Then the submission status is "DRAFT"
    And the submission workflow type is "TRADE_BREAK"
    And the submission submitted by is "alice"
    And the submission current step is 1

  Scenario: Creating a submission for an already-pending workflow type fails
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" creates a submission for workflow type "TRADE_BREAK" with display name "Trade Break"
    Then a SubmissionAlreadyExistsException is thrown

  Scenario: Maker submits draft for approval
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And the submission has complete draft configs
    When user "alice" submits the submission for approval
    Then the submission status is "PENDING_APPROVAL"
    And the submission submitted at is set

  Scenario: Non-owner cannot submit another user's draft
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And the submission has complete draft configs
    When user "bob" submits the submission for approval
    Then an IllegalStateException is thrown

  Scenario: Checker approves a pending submission and configs become active
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" approves the submission
    Then the submission status is "APPROVED"
    And 6 config documents have been published

  Scenario: Submitter cannot self-approve (maker-checker enforced)
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" approves the submission
    Then a SelfApprovalException is thrown

  Scenario: Checker rejects a pending submission with a reason
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" rejects the submission with reason "Field mappings are incorrect"
    Then the submission status is "REJECTED"
    And the rejection reason is "Field mappings are incorrect"
    And the reviewed by is "bob"

  Scenario: Approved submission is not re-approvable
    Given an APPROVED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" approves the submission
    Then an IllegalStateException is thrown

  Scenario: Rejected submission is not in pending queue
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When the pending submissions for tenant "tenant-1" are retrieved
    Then the pending submissions list is empty

  Scenario: Maker can save a partial draft at any step and current step is persisted
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" saves draft progress at step 3
    Then the submission current step is 3

  Scenario: Partial draft is returned in the maker's draft list
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When the draft submissions for user "alice" in tenant "tenant-1" are retrieved
    Then the draft submissions list contains 1 submission

  Scenario: Partial draft cannot be submitted for approval until blotter and detail view are complete
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And the submission has incomplete draft configs
    When user "alice" submits the submission for approval
    Then an IncompleteSubmissionException is thrown

  Scenario: Maker can resume a partial draft from the step where they left off
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" saves draft progress at step 4
    And the submission is loaded by id
    Then the submission current step is 4

  Scenario: Maker can revise a rejected submission — status returns to Draft
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" revises the submission with updated draft configs
    Then the submission status is "DRAFT"
    And the rejection reason is null
    And the submission current step is 1

  Scenario: Revised submission retains same id but has updated draft configs
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" revises the submission with updated draft configs
    Then the submission id is unchanged
    And the draft configs have been updated

  Scenario: Non-owner cannot revise another maker's rejected submission
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" revises the submission with updated draft configs
    Then an IllegalStateException is thrown

  Scenario: Revised submission can be resubmitted and approved
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" revises the submission with updated draft configs
    And the submission has complete draft configs
    And user "alice" submits the submission for approval
    And user "bob" approves the submission
    Then the submission status is "APPROVED"
    And 6 config documents have been published

  Scenario: Maker-checker disabled — submission auto-approves on create
    Given maker-checker is disabled
    And no submission exists for tenant "tenant-1" and workflow type "TRADE_BREAK"
    When user "alice" creates a submission for workflow type "TRADE_BREAK" with display name "Trade Break"
    Then the submission status is "APPROVED"

  Scenario: Maker-checker disabled — configs are immediately active after create
    Given maker-checker is disabled
    And no submission exists for tenant "tenant-1" and workflow type "TRADE_BREAK"
    When user "alice" creates a submission for workflow type "TRADE_BREAK" with display name "Trade Break"
    Then 6 config documents have been published

  Scenario: Submission with invalid workflowType pattern is rejected at creation
    Given no submission exists for tenant "tenant-1" and workflow type "invalid-type"
    When user "alice" creates a submission for workflow type "invalid-type" with display name "Invalid"
    Then an IllegalArgumentException is thrown

  Scenario: Get pending submissions returns only PENDING_APPROVAL items
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And a DRAFT submission exists for tenant "tenant-1" workflow type "SETTLEMENT_FIX" submitted by "alice"
    When the pending submissions for tenant "tenant-1" are retrieved
    Then the pending submissions list contains 1 submission

  # ── Audit log ─────────────────────────────────────────────────────────────

  Scenario: Creating a submission produces a SUBMISSION_CREATED audit entry
    Given no submission exists for tenant "tenant-1" and workflow type "TRADE_BREAK"
    When user "alice" creates a submission for workflow type "TRADE_BREAK" with display name "Trade Break"
    Then an audit entry of type "SUBMISSION_CREATED" is recorded for the submission
    And the audit entry records actor "alice"
    And the audit entry records previous state "null" and new state "DRAFT"

  Scenario: Submitting for approval produces a SUBMISSION_SUBMITTED_FOR_REVIEW audit entry
    Given a DRAFT submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    And the submission has complete draft configs
    When user "alice" submits the submission for approval
    Then an audit entry of type "SUBMISSION_SUBMITTED_FOR_REVIEW" is recorded for the submission
    And the audit entry records actor "alice"
    And the audit entry records previous state "DRAFT" and new state "PENDING_APPROVAL"

  Scenario: Approving a submission produces a SUBMISSION_APPROVED audit entry
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" approves the submission
    Then an audit entry of type "SUBMISSION_APPROVED" is recorded for the submission
    And the audit entry records actor "bob"
    And the audit entry records previous state "PENDING_APPROVAL" and new state "APPROVED"

  Scenario: Rejecting a submission produces a SUBMISSION_REJECTED audit entry
    Given a PENDING_APPROVAL submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "bob" rejects the submission with reason "Field mappings are incorrect"
    Then an audit entry of type "SUBMISSION_REJECTED" is recorded for the submission
    And the audit entry records actor "bob"
    And the audit entry records previous state "PENDING_APPROVAL" and new state "REJECTED"

  Scenario: Revising a rejected submission produces a SUBMISSION_REVISED audit entry
    Given a REJECTED submission exists for tenant "tenant-1" workflow type "TRADE_BREAK" submitted by "alice"
    When user "alice" revises the submission with updated draft configs
    Then an audit entry of type "SUBMISSION_REVISED" is recorded for the submission
    And the audit entry records actor "alice"
    And the audit entry records previous state "REJECTED" and new state "DRAFT"
