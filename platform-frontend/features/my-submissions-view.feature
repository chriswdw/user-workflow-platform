@my-submissions
Feature: My Submissions view renders drafts and rejected submissions with resume/revise/discard

  These scenarios render the actual MySubmissionsView component, covering the real Resume/Revise
  hydration wiring into the wizard store and the discard confirmation gating — none of which the
  pure store scenarios in wizard-foundation.feature exercise through the UI.

  Scenario: No drafts shows the empty state
    When I render My Submissions
    Then My Submissions shows no drafts message

  Scenario: A draft submission is listed with its details
    Given a draft submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" at step 3
    When I render My Submissions
    Then My Submissions lists the draft "Settlement Exception"

  Scenario: A rejected submission is listed with its rejection reason
    Given a rejected submission "TRADE_BREAK" for "Trade Break" with reason "Missing detail view config"
    When I render My Submissions
    Then My Submissions lists the rejected submission "Trade Break" with reason "Missing detail view config"

  Scenario: Clicking Resume hydrates the wizard store and opens the wizard
    Given a draft submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" at step 3
    When I render My Submissions
    And I click Resume on the draft row
    Then the wizard store is hydrated for resuming that submission

  Scenario: Clicking Revise hydrates the wizard store and opens the wizard
    Given a rejected submission "TRADE_BREAK" for "Trade Break" with reason "Missing detail view config"
    When I render My Submissions
    And I click Revise on the rejected row
    Then the wizard store is hydrated for revising that submission

  Scenario: Discarding a draft after confirming sends the discard request
    Given a draft submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" at step 3
    When I render My Submissions
    And I click Discard
    Then the discard request is sent

  Scenario: Declining the discard confirmation does not send a request
    Given a draft submission "SETTLEMENT_EXCEPTION" for "Settlement Exception" at step 3
    And the user will decline the discard confirmation
    When I render My Submissions
    And I click Discard
    Then no discard request is sent
