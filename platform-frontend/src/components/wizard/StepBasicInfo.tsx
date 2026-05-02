import { useWizardStore } from '../../store/wizardStore';

const WORKFLOW_TYPE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export function StepBasicInfo() {
  const { workflowType, displayName, description, setWorkflowType, setDisplayName, setDescription } =
    useWizardStore();

  const typeInvalid = workflowType.length > 0 && !WORKFLOW_TYPE_PATTERN.test(workflowType);

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Basic Information</h2>

      <div className="form-field">
        <label htmlFor="workflowType" className="form-label">
          Workflow Type <span className="required">*</span>
        </label>
        <input
          id="workflowType"
          type="text"
          className={`form-input${typeInvalid ? ' form-input--error' : ''}`}
          value={workflowType}
          onChange={e => setWorkflowType(e.target.value)}
          placeholder="e.g. TRADE_LIFECYCLE"
        />
        {typeInvalid && (
          <p className="form-error">Must start with an uppercase letter and contain only uppercase letters, digits, or underscores.</p>
        )}
      </div>

      <div className="form-field">
        <label htmlFor="displayName" className="form-label">
          Display Name <span className="required">*</span>
        </label>
        <input
          id="displayName"
          type="text"
          className="form-input"
          value={displayName}
          onChange={e => setDisplayName(e.target.value)}
          placeholder="e.g. Trade Lifecycle"
        />
      </div>

      <div className="form-field">
        <label htmlFor="description" className="form-label">Description</label>
        <textarea
          id="description"
          className="form-input"
          rows={3}
          value={description}
          onChange={e => setDescription(e.target.value)}
          placeholder="Optional description"
        />
      </div>
    </div>
  );
}
