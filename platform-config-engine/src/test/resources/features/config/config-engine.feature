Feature: Config engine — loading and cross-schema validation

  Scenario: Load active routing config for a known tenant and workflow type
    Given tenant "tenant-1" has an active routing config "config-r-001" for workflow type "SETTLEMENT_EXCEPTION"
    When I load the active ROUTING_CONFIG for tenant "tenant-1" and workflow type "SETTLEMENT_EXCEPTION"
    Then the returned config document has id "config-r-001"

  Scenario: Loading fails when no active config exists
    Given no active routing config exists for tenant "tenant-1" and workflow type "TRADE_BREAK"
    When I attempt to load the active ROUTING_CONFIG for tenant "tenant-1" and workflow type "TRADE_BREAK"
    Then a ConfigNotFoundException is thrown

  Scenario: Loading fails when multiple active configs exist for the same workflow type
    Given tenant "tenant-1" has two active routing configs for workflow type "SETTLEMENT_EXCEPTION"
    When I attempt to load the active ROUTING_CONFIG for tenant "tenant-1" and workflow type "SETTLEMENT_EXCEPTION"
    Then a ConfigIntegrityException is thrown

  Scenario: validateConfigs passes when routing config defaultGroup references an active resolution group
    Given tenant "tenant-1" has an active routing config for workflow type "SETTLEMENT_EXCEPTION" with defaultGroup "group-ops"
    And tenant "tenant-1" has an active resolution group with id "group-ops"
    When I validate configs for tenant "tenant-1"
    Then the validation result has no violations

  Scenario: validateConfigs fails when routing config defaultGroup references an unknown group
    Given tenant "tenant-1" has an active routing config for workflow type "SETTLEMENT_EXCEPTION" with defaultGroup "group-unknown"
    And no resolution group with id "group-unknown" exists for tenant "tenant-1"
    When I validate configs for tenant "tenant-1"
    Then the validation result has a violation mentioning "defaultGroup"

  Scenario: validateConfigs fails when duplicate active routing configs exist for the same workflow type
    Given tenant "tenant-1" has two active routing configs for workflow type "SETTLEMENT_EXCEPTION"
    When I validate configs for tenant "tenant-1"
    Then the validation result has a violation mentioning "duplicate active"
