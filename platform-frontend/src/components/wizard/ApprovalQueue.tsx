import { useState } from 'react';
import { usePendingSubmissions } from '../../api/useWorkflowTypeSubmissions';
import { useApproveSubmission, useRejectSubmission } from '../../api/useSubmissionActions';
import type { WorkflowTypeSubmission } from '../../types/WorkflowTypeSubmission';

interface RejectFormProps {
  readonly submissionId: string;
  readonly onDone: () => void;
}

interface ApprovalRowProps {
  readonly sub: WorkflowTypeSubmission;
}

function RejectForm({ submissionId, onDone }: RejectFormProps) {
  const [reason, setReason] = useState('');
  const reject = useRejectSubmission(submissionId);

  return (
    <div className="reject-form">
      <textarea
        className="form-input"
        rows={2}
        value={reason}
        onChange={e => setReason(e.target.value)}
        placeholder="Rejection reason"
      />
      <div className="button-row">
        <button
          type="button"
          className="btn btn-danger"
          onClick={() => reject.mutate({ rejectionReason: reason }, { onSuccess: onDone })}
          disabled={!reason.trim() || reject.isPending}
        >
          Confirm Reject
        </button>
        <button type="button" className="btn btn-secondary" onClick={onDone}>Cancel</button>
      </div>
    </div>
  );
}

function ApprovalRow({ sub }: ApprovalRowProps) {
  const [rejecting, setRejecting] = useState(false);
  const approve = useApproveSubmission(sub.id);

  return (
    <tr>
      <td><code>{sub.workflowType}</code></td>
      <td>{sub.displayName}</td>
      <td>{sub.submittedBy}</td>
      <td>{sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : '—'}</td>
      <td>
        {rejecting ? (
          <RejectForm submissionId={sub.id} onDone={() => setRejecting(false)} />
        ) : (
          <div className="button-row">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => approve.mutate()}
              disabled={approve.isPending}
            >
              Approve
            </button>
            <button type="button" className="btn btn-danger" onClick={() => setRejecting(true)}>
              Reject
            </button>
          </div>
        )}
      </td>
    </tr>
  );
}

export function ApprovalQueue() {
  const { data: submissions = [], isLoading, isError } = usePendingSubmissions();

  if (isLoading) return <p className="status-text">Loading…</p>;
  if (isError) return <p className="status-text status-text--error">Failed to load pending submissions.</p>;
  if (submissions.length === 0) return <p className="status-text">No submissions pending approval.</p>;

  return (
    <div className="view-container">
      <h2 className="view-title">Pending Approvals ({submissions.length})</h2>
      <table className="data-table">
        <thead>
          <tr>
            <th>Workflow Type</th>
            <th>Display Name</th>
            <th>Submitted By</th>
            <th>Submitted At</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {submissions.map(sub => <ApprovalRow key={sub.id} sub={sub} />)}
        </tbody>
      </table>
    </div>
  );
}
