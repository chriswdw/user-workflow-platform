@approval-queue
Feature: Approval queue renders pending submissions with approve/reject actions

  These scenarios render the actual ApprovalQueue component, covering the real approve/reject
  mutation wiring and the reject-form's reason-required gating — a maker-checker-adjacent UI
  behaviour with no equivalent coverage elsewhere in the suite.

  Scenario: No pending submissions shows the empty state
    When I render the approval queue
    Then the approval queue shows the empty state

  Scenario: A pending submission is listed
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    Then the approval queue lists 1 pending submission

  Scenario: Clicking Approve sends the approve request
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    And I click Approve
    Then the approve request is sent

  Scenario: Clicking Reject reveals the reject form with Confirm Reject disabled until a reason is entered
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    And I click Reject
    Then the reject form is shown
    And the Confirm Reject button is disabled

  Scenario: Typing a rejection reason enables Confirm Reject
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    And I click Reject
    And I type "Missing detail view config" as the rejection reason
    Then the Confirm Reject button is enabled

  Scenario: Confirming the reject sends the request with the typed reason
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    And I click Reject
    And I type "Missing detail view config" as the rejection reason
    And I click Confirm Reject
    Then the reject request is sent with reason "Missing detail view config"

  Scenario: Cancelling the reject form hides it without sending a request
    Given a pending submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" submitted by "maker-1"
    When I render the approval queue
    And I click Reject
    And I click Cancel on the reject form
    Then the reject form is not shown
