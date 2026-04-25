Feature: Work item routing

  The routing engine assigns every work item to a resolution group using an ordered rule set.
  Rules are evaluated in ascending priority order; the first match wins.
  If no rule matches, the item is assigned to the configured defaultGroup.
  An unrouted work item is not permitted — the domain invariant is enforced structurally
  by requiring defaultGroup on every RoutingConfig.

  Background:
    Given a tenant "ACME" with id "tenant-001"
    And a resolution group "Settlement Team" with id "group-settlement"
    And a resolution group "Default Queue" with id "group-default"
    And a routing config for workflow type "SETTLEMENT_EXCEPTION" with default group "group-default"

  Scenario: Work item matching a routing rule is assigned to the target group
    Given the config has a rule with priority 10 matching "counterparty.region" EQ "EMEA" routing to "group-settlement"
    When a work item is routed with fields:
      | counterparty.region | EMEA |
    Then the work item is assigned to "group-settlement"
    And routedByDefault is false

  Scenario: Work item not matching any rule is assigned to the defaultGroup
    Given the config has a rule with priority 10 matching "counterparty.region" EQ "EMEA" routing to "group-settlement"
    When a work item is routed with fields:
      | counterparty.region | APAC |
    Then the work item is assigned to "group-default"
    And routedByDefault is true
    And an audit entry is written with event type "ROUTING_FALLBACK"

  Scenario: First matching rule wins when multiple rules could match
    Given a resolution group "Compliance Team" with id "group-compliance"
    And the config has a rule with priority 10 matching "counterparty.region" EQ "EMEA" routing to "group-settlement"
    And the config has a rule with priority 20 matching "counterparty.region" EQ "EMEA" routing to "group-compliance"
    When a work item is routed with fields:
      | counterparty.region | EMEA |
    Then the work item is assigned to "group-settlement"

  Scenario: Inactive rules are skipped during evaluation
    Given the config has an inactive rule with priority 10 matching "counterparty.region" EQ "EMEA" routing to "group-settlement"
    When a work item is routed with fields:
      | counterparty.region | EMEA |
    Then the work item is assigned to "group-default"
    And routedByDefault is true

  Scenario: Empty rule set routes all items to defaultGroup
    When a work item is routed with fields:
      | counterparty.region | EMEA |
    Then the work item is assigned to "group-default"
    And routedByDefault is true
    And an audit entry is written with event type "ROUTING_FALLBACK"

  Scenario: Nested AND condition — all conditions must match
    Given the config has a rule with priority 10 routing to "group-settlement" matching:
      """
      {
        "type": "GROUP",
        "logicalOperator": "AND",
        "children": [
          { "type": "LEAF", "field": "counterparty.region", "operator": "EQ", "value": "EMEA" },
          { "type": "LEAF", "field": "trade.notionalAmount.amount", "operator": "GTE", "value": "1000000.00" }
        ]
      }
      """
    When a work item is routed with fields:
      | counterparty.region         | EMEA       |
      | trade.notionalAmount.amount | 1500000.00 |
    Then the work item is assigned to "group-settlement"

  Scenario: Nested AND condition — partial match falls through to defaultGroup
    Given the config has a rule with priority 10 routing to "group-settlement" matching:
      """
      {
        "type": "GROUP",
        "logicalOperator": "AND",
        "children": [
          { "type": "LEAF", "field": "counterparty.region", "operator": "EQ", "value": "EMEA" },
          { "type": "LEAF", "field": "trade.notionalAmount.amount", "operator": "GTE", "value": "1000000.00" }
        ]
      }
      """
    When a work item is routed with fields:
      | counterparty.region         | EMEA      |
      | trade.notionalAmount.amount | 500000.00 |
    Then the work item is assigned to "group-default"
    And routedByDefault is true

  Scenario: Nested OR condition — either condition matching is sufficient
    Given the config has a rule with priority 10 routing to "group-settlement" matching:
      """
      {
        "type": "GROUP",
        "logicalOperator": "OR",
        "children": [
          { "type": "LEAF", "field": "counterparty.region", "operator": "EQ", "value": "EMEA" },
          { "type": "LEAF", "field": "trade.flag", "operator": "EQ", "value": "REGULATORY_HOLD" }
        ]
      }
      """
    When a work item is routed with fields:
      | counterparty.region | APAC            |
      | trade.flag          | REGULATORY_HOLD |
    Then the work item is assigned to "group-settlement"

  Scenario: Routing a matched item produces an ASSIGNMENT audit entry
    Given the config has a rule with priority 10 matching "counterparty.region" EQ "EMEA" routing to "group-settlement"
    When a work item is routed with fields:
      | counterparty.region | EMEA |
    Then an audit entry is written with event type "ASSIGNMENT"
