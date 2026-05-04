import { useMyDraftSubmissions, useMyRejectedSubmissions } from '../../api/useWorkflowTypeSubmissions';
import { useWizardStore } from '../../store/wizardStore';
import type { WorkflowTypeSubmission } from '../../types/WorkflowTypeSubmission';

interface MySubmissionsViewProps {
  readonly onOpenWizard: () => void;
}

interface DraftRowProps {
  readonly sub: WorkflowTypeSubmission;
  readonly onOpenWizard: () => void;
}

interface RejectedRowProps {
  readonly sub: WorkflowTypeSubmission;
  readonly onOpenWizard: () => void;
}

function DraftRow({ sub, onOpenWizard }: DraftRowProps) {
  const { hydrateForResume } = useWizardStore();

  function handleResume() {
    hydrateForResume(sub);
    onOpenWizard();
  }

  return (
    <tr>
      <td><code>{sub.workflowType}</code></td>
      <td>{sub.displayName}</td>
      <td>Step {sub.currentStep}</td>
      <td>{new Date(sub.updatedAt).toLocaleString()}</td>
      <td>
        <button type="button" className="btn btn-secondary" onClick={handleResume}>Resume</button>
      </td>
    </tr>
  );
}

function RejectedRow({ sub, onOpenWizard }: RejectedRowProps) {
  const { hydrateForRevision } = useWizardStore();

  function handleRevise() {
    hydrateForRevision(sub);
    onOpenWizard();
  }

  return (
    <tr>
      <td><code>{sub.workflowType}</code></td>
      <td>{sub.displayName}</td>
      <td className="rejection-reason">{sub.rejectionReason ?? '—'}</td>
      <td>{new Date(sub.updatedAt).toLocaleString()}</td>
      <td>
        <button type="button" className="btn btn-secondary" onClick={handleRevise}>Revise</button>
      </td>
    </tr>
  );
}

export function MySubmissionsView({ onOpenWizard }: MySubmissionsViewProps) {
  const { data: drafts = [], isLoading: loadingDrafts } = useMyDraftSubmissions();
  const { data: rejected = [], isLoading: loadingRejected } = useMyRejectedSubmissions();

  return (
    <div className="view-container">
      <section>
        <h2 className="view-title">In Progress</h2>
        {loadingDrafts && <p className="status-text">Loading…</p>}
        {!loadingDrafts && drafts.length === 0 && <p className="status-text">No drafts in progress.</p>}
        {drafts.length > 0 && (
          <table className="data-table">
            <thead>
              <tr>
                <th>Workflow Type</th>
                <th>Display Name</th>
                <th>Progress</th>
                <th>Last Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {drafts.map(sub => <DraftRow key={sub.id} sub={sub} onOpenWizard={onOpenWizard} />)}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2 className="view-title">Needs Attention</h2>
        {loadingRejected && <p className="status-text">Loading…</p>}
        {!loadingRejected && rejected.length === 0 && <p className="status-text">No rejected submissions.</p>}
        {rejected.length > 0 && (
          <table className="data-table">
            <thead>
              <tr>
                <th>Workflow Type</th>
                <th>Display Name</th>
                <th>Rejection Reason</th>
                <th>Last Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rejected.map(sub => <RejectedRow key={sub.id} sub={sub} onOpenWizard={onOpenWizard} />)}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
