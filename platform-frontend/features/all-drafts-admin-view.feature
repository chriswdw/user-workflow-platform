@all-drafts-admin
Feature: All Drafts admin view lets platform admins inspect and act on any tenant's drafts

  Covers the admin-only cross-tenant draft list, its status-conditional action buttons (DRAFT
  gets Continue+Discard, REJECTED gets Revise only), and the discard confirmation modal flow —
  none of which is exercised anywhere else in the suite.

  Scenario: No drafts shows the empty state
    When I render the all drafts admin view
    Then the all drafts admin view lists 0 draft

  Scenario: Clicking a row opens the submission detail panel
    Given an admin-visible draft "SETTLEMENT_EXCEPTION" by "maker-1" at step 3
    When I render the all drafts admin view
    Then no submission detail is shown
    When I click the draft row
    Then the submission detail panel is shown for "Settlement Exception"

  Scenario: A draft submission's detail panel offers Continue in Wizard and Discard, not Revise
    Given an admin-visible draft "SETTLEMENT_EXCEPTION" by "maker-1" at step 3
    When I render the all drafts admin view
    And I click the draft row
    Then the detail panel shows a Continue in Wizard button but no Revise button

  Scenario: A rejected submission's detail panel offers Revise, not Continue in Wizard
    Given an admin-visible rejected submission "TRADE_BREAK" by "maker-2"
    When I render the all drafts admin view
    And I click the draft row
    Then the detail panel shows a Revise button but no Continue in Wizard button

  Scenario: Clicking Continue in Wizard hydrates the wizard store and opens the wizard
    Given an admin-visible draft "SETTLEMENT_EXCEPTION" by "maker-1" at step 3
    When I render the all drafts admin view
    And I click the draft row
    And I click Continue in Wizard
    Then the admin wizard store is hydrated for resuming and onOpenWizard fires

  Scenario: Discarding a draft goes through the confirmation modal before sending the request
    Given an admin-visible draft "SETTLEMENT_EXCEPTION" by "maker-1" at step 3
    When I render the all drafts admin view
    And I click the draft row
    And I click Discard in the admin detail panel
    And I confirm the discard in the modal
    Then the admin discard request is sent
