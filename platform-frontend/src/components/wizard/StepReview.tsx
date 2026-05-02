import { useWizardStore } from '../../store/wizardStore';

interface Props {
  submitError: string | null;
  onGoToStep: (step: number) => void;
}

export function StepReview({ submitError, onGoToStep }: Props) {
  const { workflowType, displayName, description, sourceType, sourceConnectionId, fieldMappings, idempotencyKeyField, blotterColumns, detailSections } =
    useWizardStore();

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Review</h2>

      {submitError && (
        <div className="error-banner">
          {submitError}
          {/step (\d+)/i.test(submitError) && (
            <button
              type="button"
              className="btn btn-link"
              onClick={() => {
                const match = /step (\d+)/i.exec(submitError);
                if (match) onGoToStep(Number(match[1]));
              }}
            >
              Go to that step
            </button>
          )}
        </div>
      )}

      <section className="review-section">
        <h3>Basic Info</h3>
        <dl className="review-dl">
          <dt>Workflow Type</dt><dd>{workflowType}</dd>
          <dt>Display Name</dt><dd>{displayName}</dd>
          {description && <><dt>Description</dt><dd>{description}</dd></>}
        </dl>
      </section>

      <section className="review-section">
        <h3>Source</h3>
        <dl className="review-dl">
          <dt>Type</dt><dd>{sourceType}</dd>
          {sourceConnectionId && <><dt>Connection ID</dt><dd>{sourceConnectionId}</dd></>}
        </dl>
      </section>

      <section className="review-section">
        <h3>Field Mappings ({fieldMappings.length})</h3>
        <ul className="review-list">
          {fieldMappings.map(m => (
            <li key={m.fieldPath}>
              <code>{m.fieldPath}</code> — {m.type}{m.required ? ' (required)' : ''}
              {idempotencyKeyField === m.fieldPath && <span className="badge badge--info"> idempotency key</span>}
            </li>
          ))}
        </ul>
      </section>

      <section className="review-section">
        <h3>Blotter Columns ({blotterColumns.length})</h3>
        <ul className="review-list">
          {blotterColumns.map(c => (
            <li key={c.fieldPath}>{c.headerName} (<code>{c.fieldPath}</code>)</li>
          ))}
        </ul>
      </section>

      <section className="review-section">
        <h3>Detail View Sections ({detailSections.length})</h3>
        <ul className="review-list">
          {detailSections.map(s => (
            <li key={s.sectionName}>{s.sectionName} — {s.fields.length} field(s)</li>
          ))}
        </ul>
      </section>
    </div>
  );
}
