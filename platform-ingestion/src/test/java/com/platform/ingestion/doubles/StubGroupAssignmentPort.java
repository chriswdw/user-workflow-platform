package com.platform.ingestion.doubles;

import com.platform.ingestion.domain.ports.out.IGroupAssignmentPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory double for {@link IGroupAssignmentPort}. Returns a preconfigured group per workflow
 * type, can be told to fail, and records every request it receives so scenarios can assert what
 * ingestion asked the router.
 */
public class StubGroupAssignmentPort implements IGroupAssignmentPort {

  private final Map<String, AssignmentResult> resultByWorkflowType = new HashMap<>();
  private final List<AssignmentRequest> requests = new ArrayList<>();
  private RuntimeException failure;

  public void configure(String workflowType, String groupId) {
    configure(workflowType, groupId, false);
  }

  public void configure(String workflowType, String groupId, boolean routedByDefault) {
    resultByWorkflowType.put(workflowType, new AssignmentResult(groupId, routedByDefault));
  }

  /** Makes every subsequent assignment throw, as the real adapter does when routing fails. */
  public void failWith(RuntimeException failure) {
    this.failure = failure;
  }

  public List<AssignmentRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  public AssignmentResult assignGroup(AssignmentRequest request) {
    requests.add(request);
    if (failure != null) {
      throw failure;
    }
    return resultByWorkflowType.getOrDefault(
        request.workflowType(), new AssignmentResult("default-group", false));
  }
}
