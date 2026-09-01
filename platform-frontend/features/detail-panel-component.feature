@detail-panel
Feature: Detail panel renders work item details and audit trail

  These scenarios render the actual DetailPanel component (with its Details/Audit Trail tabs)
  through the shared jsdom + React Testing Library harness — the real tab-switching state and
  audit rendering logic, not just the pure field-resolution helpers covered elsewhere.

  Scenario: Detail panel shows the work item id and status, opening on the Details tab
    When I render the detail panel for that work item
    Then the detail panel shows the work item id and status
    And the details tab panel is visible and the audit tab panel is hidden

  Scenario: Clicking the Audit Trail tab switches the visible panel
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    Then the audit tab panel is visible and the details tab panel is hidden

  Scenario: Audit trail entries are listed once the Audit Trail tab is open
    Given an audit entry with event type "STATE_TRANSITION" for the work item
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    Then an audit entry with event type "STATE_TRANSITION" is shown in the audit trail

  Scenario: Clicking the close button fires onClose
    When I render the detail panel for that work item
    And I click the close button
    Then the onClose callback fires

  Scenario: An audit entry with changed fields expands to show previous/new values on click
    Given an audit entry with event type "FIELD_UPDATE" for the work item with these changed fields:
      | fieldPath        | previousValue | newValue |
      | trade.notional    | 1000          | 2000     |
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    Then the changed-fields detail table for that audit entry is not shown
    When I click the audit entry row
    Then the changed-fields detail table for that audit entry is shown
    And the changed field "trade.notional" shows previous value "1000" and new value "2000"

  Scenario: Clicking an expanded audit entry row again collapses it
    Given an audit entry with event type "FIELD_UPDATE" for the work item with these changed fields:
      | fieldPath     | previousValue | newValue |
      | trade.notional | 1000          | 2000     |
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    And I click the audit entry row
    And I click the audit entry row
    Then the changed-fields detail table for that audit entry is not shown

  Scenario: An audit entry with no changed fields is not clickable and shows no expand indicator
    Given an audit entry with event type "STATE_TRANSITION" for the work item
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    Then the audit entry row shows no expand indicator
    When I click the audit entry row
    Then the changed-fields detail table for that audit entry is not shown

  Scenario: A null changed-field value renders as an em dash
    Given an audit entry with event type "FIELD_UPDATE" for the work item with these changed fields:
      | fieldPath          | previousValue | newValue |
      | trade.settlementRef |               | SR-9     |
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    And I click the audit entry row
    Then the changed field "trade.settlementRef" shows previous value "—" and new value "SR-9"

  Scenario: An object-valued changed field renders as JSON
    Given an audit entry with event type "FIELD_UPDATE" for the work item with an object-valued changed field "trade.counterparty"
    When I render the detail panel for that work item
    And I click the Audit Trail tab
    And I click the audit entry row
    Then the changed field "trade.counterparty" shows its new value as JSON

  Scenario: A field restricted to a role the viewer doesn't hold is not shown
    Given a detail section field "counterparty.legalEntity" visible only to role "SUPERVISOR"
    And the current viewer has role "ANALYST"
    When I render the detail panel for that work item
    Then the field "counterparty.legalEntity" is not shown in the details panel

  Scenario: A field restricted to a role the viewer holds is shown
    Given a detail section field "counterparty.legalEntity" visible only to role "SUPERVISOR"
    And the current viewer has role "SUPERVISOR"
    When I render the detail panel for that work item
    Then the field "counterparty.legalEntity" is visible in the details panel

  Scenario: A field editable in the work item's current state renders as an input
    Given a detail section field "resolution.notes" editable while status is "UNDER_REVIEW"
    When I render the detail panel for that work item
    Then the field "resolution.notes" renders as an editable input

  # ── Actions: ActionButton / ActionFormModal / ConfirmModal ────────────────────────────────

  Scenario: A direct action (no confirmation, no input fields) fires its transition immediately
    Given a direct action "Close Item" for transition "close"
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Close Item" action button
    Then the transition "close" was fired with no additional fields

  Scenario: A confirmation-required action opens a dialog and fires the transition on confirm
    Given a confirmation-required action "Reject" for transition "reject" with message "Are you sure you want to reject this item?"
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Reject" action button
    Then a confirmation dialog showing "Are you sure you want to reject this item?" is visible
    When I click Confirm in the confirmation dialog
    Then the transition "reject" was fired with no additional fields

  Scenario: A failed confirmation-required transition shows the server error in the dialog
    Given a confirmation-required action "Reject" for transition "reject" with message "Are you sure?"
    And the transition will fail with message "Item is locked by another user"
    When I render the detail panel for that work item
    And I click the "Reject" action button
    And I click Confirm in the confirmation dialog
    Then the confirmation dialog shows the server error "Item is locked by another user"

  Scenario: Cancelling a confirmation dialog does not fire the transition
    Given a confirmation-required action "Reject" for transition "reject" with message "Are you sure?"
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Reject" action button
    And I click Cancel in the confirmation dialog
    Then no transition was fired

  Scenario: An action with input fields opens a form; submitting valid values fires the transition with them
    Given an action "Escalate" for transition "escalate" with these input fields:
      | field  | label  | inputType | required |
      | reason | Reason | TEXTAREA  | true     |
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Escalate" action button
    And I type "Breach exceeds tolerance" into the "Reason" field
    And I click Submit in the action form
    Then the transition "escalate" was fired with additional fields:
      | reason | Breach exceeds tolerance |

  Scenario: Leaving a required input field blank shows a validation error and does not fire the transition
    Given an action "Escalate" for transition "escalate" with these input fields:
      | field  | label  | inputType | required |
      | reason | Reason | TEXTAREA  | true     |
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Escalate" action button
    And I click Submit in the action form
    Then the action form shows a validation error for "Reason"
    And no transition was fired

  Scenario: Cancelling an action form does not fire the transition
    Given an action "Escalate" for transition "escalate" with these input fields:
      | field  | label  | inputType | required |
      | reason | Reason | TEXTAREA  | true     |
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Escalate" action button
    And I click Cancel in the action form
    Then no transition was fired

  Scenario: A select input field offers its configured options
    Given an action "Categorize" for transition "categorize" with these input fields:
      | field    | label    | inputType | required | options       |
      | category | Category | SELECT    | true     | Fraud,Dispute |
    When I render the detail panel for that work item
    And I click the "Categorize" action button
    Then the "Category" field offers options "Fraud,Dispute"

  Scenario: A currency input field rejects an invalid amount
    Given an action "Adjust" for transition "adjust" with these input fields:
      | field  | label  | inputType | required |
      | amount | Amount | CURRENCY  | true     |
    And the transition will succeed
    When I render the detail panel for that work item
    And I click the "Adjust" action button
    And I type "not-a-number" into the "Amount" field
    And I click Submit in the action form
    Then the action form shows a validation error for "Amount"
    And no transition was fired

  Scenario: A server-side transition failure is shown inside the action form
    Given an action "Escalate" for transition "escalate" with these input fields:
      | field  | label  | inputType | required |
      | reason | Reason | TEXTAREA  | true     |
    And the transition will fail with message "Item is locked by another user"
    When I render the detail panel for that work item
    And I click the "Escalate" action button
    And I type "Breach exceeds tolerance" into the "Reason" field
    And I click Submit in the action form
    Then the action form shows the server error "Item is locked by another user"

  Scenario: A pending checker approval shows the maker-checker banner
    Given the work item has a pending checker approval for transition "close"
    When I render the detail panel for that work item
    Then the maker-checker banner shows "close"
