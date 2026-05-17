
import { useState } from 'react';
import { useAllDraftSubmissions } from '../../api/useWorkflowTypeSubmissions';
import { useDiscardSubmission } from '../../api/useSubmissionActions';
import type { WorkflowTypeSubmission } from '../../types/WorkflowTypeSubmission';
import { useWizardStore } from '../../store/wizardStore';
import { ConfirmModal } from '../detail/ConfirmModal';

const STEP_LABELS = ['', 'Basic Info', 'Source', 'Sample', 'Fields', 'Blotter', 'Detail View', 'Review'];

interface AllDraftsAdminViewProps {
  readonly onOpenWizard: () => void;
}

interface SubmissionDetailProps {
  readonly sub: WorkflowTypeSubmission;
  readonly onClose: () => void;
  readonly onOpenWizard: () => void;
}

function SubmissionDetail({ sub, onClose, onOpenWizard }: SubmissionDetailProps) {
  const { hydrateForResume, hydrateForRevision } = useWizardStore();
  const discard = useDiscardSubmission(sub.id);
  const [confirmOpen, setConfirmOpen] = useState(false);

  function handleContinue() {
    hydrateForResume(sub);
    onOpenWizard();
  }

  function handleRevise() {
    hydrateForRevision(sub);
    onOpenWizard();
  }

  function handleDiscardConfirmed() {
    discard.mutate(undefined, { onSuccess: onClose });
    setConfirmOpen(false);
  }

  return (
    <div className="submission-detail">
      <div className="submission-detail-header">
        <h3 className="submission-detail-title">{sub.displayName}</h3>
        <button type="button" className="btn btn-icon" onClick={onClose} aria-label="Close">✕</button>
      </div>
      <dl className="submission-detail-fields">
        <dt>Workflow Type</dt>
        <dd><code>{sub.workflowType}</code></dd>

        <dt>Submitted By</dt>
        <dd>{sub.submittedBy}</dd>

        <dt>Current Step</dt>
        <dd>Step {sub.currentStep} — {STEP_LABELS[sub.currentStep] ?? 'Unknown'}</dd>

        <dt>Status</dt>
        <dd>{sub.statusDisplayName}</dd>

        {sub.description && (
          <>
            <dt>Description</dt>
            <dd>{sub.description}</dd>
          </>
        )}

        <dt>Created</dt>
        <dd>{new Date(sub.createdAt).toLocaleString()}</dd>

        <dt>Last Updated</dt>
        <dd>{new Date(sub.updatedAt).toLocaleString()}</dd>
      </dl>

      <div className="submission-detail-actions">
        {sub.statusCode === 'DRAFT' && (
          <button type="button" className="btn btn-primary" onClick={handleContinue}>
            Continue in Wizard
          </button>
        )}
        {sub.statusCode === 'REJECTED' && (
          <button type="button" className="btn btn-secondary" onClick={handleRevise}>
            Revise
          </button>
        )}
        {sub.statusCode === 'DRAFT' && (
          <button type="button" className="btn btn-danger" onClick={() => setConfirmOpen(true)}
                  disabled={discard.isPending}>
            Discard
          </button>
        )}
      </div>

      {confirmOpen && (
        <ConfirmModal
          message="Discard this draft? This cannot be undone."
          onConfirm={handleDiscardConfirmed}
          onCancel={() => setConfirmOpen(false)}
        />
      )}
    </div>
  );
}

export function AllDraftsAdminView({ onOpenWizard }: AllDraftsAdminViewProps) {
  const { data: drafts = [], isLoading, isError } = useAllDraftSubmissions();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = drafts.find(d => d.id === selectedId) ?? null;

  if (isLoading) return <p className="status-text">Loading…</p>;
  if (isError) return <p className="status-text status-text--error">Failed to load draft submissions.</p>;

  return (
    <div className={`admin-split${selected ? ' admin-split--open' : ''}`}>
      <div className="view-container admin-split-main">
        <h2 className="view-title">All Draft Submissions ({drafts.length})</h2>
        {drafts.length === 0 ? (
          <p className="status-text">No draft submissions in progress.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Workflow Type</th>
                <th>Display Name</th>
                <th>Submitted By</th>
                <th>Step</th>
                <th>Last Updated</th>
              </tr>
            </thead>
            <tbody>
              {drafts.map(sub => (
                <tr
                  key={sub.id}
                  className={`data-table-row--clickable${selectedId === sub.id ? ' data-table-row--selected' : ''}`}
                  onClick={() => setSelectedId(sub.id === selectedId ? null : sub.id)}
                >
                  <td><code>{sub.workflowType}</code></td>
                  <td>{sub.displayName}</td>
                  <td>{sub.submittedBy}</td>
                  <td>{STEP_LABELS[sub.currentStep] ?? `Step ${sub.currentStep}`}</td>
                  <td>{new Date(sub.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {selected && (
        <SubmissionDetail sub={selected} onClose={() => setSelectedId(null)} onOpenWizard={onOpenWizard} />
      )}
    </div>
  );
}
