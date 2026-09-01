Feature: Wizard step components render real business behaviour

  These scenarios render the actual React components (not just the pure store/utils logic covered
  elsewhere) via the shared jsdom + React Testing Library harness in step-definitions/support/.
  This is the first component-level scenario in the BDD suite — it exists to prove that harness
  works end to end before further component scenarios are layered on top of it.

  Scenario: The source configuration step offers all four source types with none pre-selected
    Given a fresh wizard store
    When I render the source configuration step
    Then all four source type options are shown
    And no source type is pre-selected
