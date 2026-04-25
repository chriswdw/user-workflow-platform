Feature: Workflow state machine transitions

  Background:
    Given a tenant "Bank A" with id "tenant-1"
    And a work item "item-001" of workflow type "SETTLEMENT_EXCEPTION" in state "UNDER_REVIEW" assigned to group "group-ops"
    And a workflow config for type "SETTLEMENT_EXCEPTION" with transitions:
      | name             | fromState    | toState   | allowedRoles    | requiresMakerChecker |
      | close-resolved   | UNDER_REVIEW | CLOSED    | ANALYST         | false                |
      | forward-compliance | UNDER_REVIEW | COMPLIANCE_REVIEW | SUPERVISOR | false               |

  Scenario: Authorised user transitions a work item to a new state
    When user "alice" with role "ANALYST" executes transition "close-resolved"
    Then the work item status is "CLOSED"
    And a STATE_TRANSITION audit entry is written

  Scenario: Unauthorised role is rejected
    When user "bob" with role "VIEWER" attempts transition "close-resolved"
    Then a ForbiddenTransitionException is thrown

  Scenario: Transition from wrong fromState is rejected
    Given the work item is in state "CLOSED"
    When user "alice" with role "ANALYST" attempts transition "close-resolved"
    Then an InvalidTransitionException is thrown

  Scenario: REASSIGN_GROUP action changes the assigned group
    Given the workflow config has a transition "escalate" from "UNDER_REVIEW" to "UNDER_REVIEW" for role "ANALYST" with REASSIGN_GROUP action targeting "group-escalations"
    When user "alice" with role "ANALYST" executes transition "escalate"
    Then the work item is assigned to group "group-escalations"
    And a GROUP_REASSIGNMENT audit entry is written

  Scenario: Transition with EXISTS validation rule blocks when required field is absent
    Given the workflow config has a transition "close-with-reason" from "UNDER_REVIEW" to "CLOSED" for role "ANALYST" requiring field "resolution.reason" EXISTS
    When user "alice" with role "ANALYST" attempts transition "close-with-reason"
    Then a ValidationFailedException is thrown

  Scenario: Transition with EXISTS validation rule proceeds when required field is present
    Given the workflow config has a transition "close-with-reason" from "UNDER_REVIEW" to "CLOSED" for role "ANALYST" requiring field "resolution.reason" EXISTS
    And the work item has field "resolution.reason" set to "Matched and settled"
    When user "alice" with role "ANALYST" executes transition "close-with-reason"
    Then the work item status is "CLOSED"
    And a STATE_TRANSITION audit entry is written
