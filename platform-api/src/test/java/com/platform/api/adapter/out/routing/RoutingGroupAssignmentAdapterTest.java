package com.platform.api.adapter.out.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort.AssignmentRequest;
import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort.AssignmentResult;
import com.platform.routing.domain.exception.RoutingConfigNotFoundException;
import com.platform.routing.domain.model.RoutingResult;
import com.platform.routing.domain.model.WorkItemToRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingGroupAssignmentAdapterTest {

  private static final AssignmentRequest REQUEST =
      new AssignmentRequest(
          "wi-1",
          "tenant-1",
          "SETTLEMENT_EXCEPTION",
          "maker-1",
          Map.of("trade", Map.of("ref", "T1")));

  private final List<WorkItemToRoute> routed = new ArrayList<>();

  @Test
  void translatesTheRequestIntoWhatTheRoutingEngineNeeds() {
    var adapter =
        new RoutingGroupAssignmentAdapter(item -> {
          routed.add(item);
          return new RoutingResult("group-senior", false, "rule-1");
        });

    adapter.assignGroup(REQUEST);

    assertThat(routed)
        .containsExactly(
            new WorkItemToRoute(
                "wi-1",
                "tenant-1",
                "SETTLEMENT_EXCEPTION",
                "maker-1",
                Map.of("trade", Map.of("ref", "T1"))));
  }

  @Test
  void returnsTheMatchedGroupAsNotRoutedByDefault() {
    var adapter =
        new RoutingGroupAssignmentAdapter(
            item -> new RoutingResult("group-senior", false, "rule-1"));

    assertThat(adapter.assignGroup(REQUEST)).isEqualTo(new AssignmentResult("group-senior", false));
  }

  @Test
  void returnsTheFallbackGroupAsRoutedByDefault() {
    var adapter =
        new RoutingGroupAssignmentAdapter(item -> new RoutingResult("group-ops", true, null));

    assertThat(adapter.assignGroup(REQUEST)).isEqualTo(new AssignmentResult("group-ops", true));
  }

  @Test
  void aRoutingFailurePropagatesUnchangedSoIngestionCannotGuessAGroup() {
    var failure = new RoutingConfigNotFoundException("SETTLEMENT_EXCEPTION", "tenant-1");
    var adapter =
        new RoutingGroupAssignmentAdapter(item -> {
          throw failure;
        });

    assertThatThrownBy(() -> adapter.assignGroup(REQUEST)).isSameAs(failure);
  }
}
