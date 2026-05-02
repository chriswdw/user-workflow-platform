import { useWizardStore, type SourceType } from '../../store/wizardStore';
import { useSourceConnections } from '../../api/useSourceConnections';

const SOURCE_TYPES: Array<{ value: SourceType; label: string }> = [
  { value: 'KAFKA', label: 'Kafka Topic' },
  { value: 'DB_POLL', label: 'Database Poll' },
  { value: 'FILE_SHARE', label: 'File Share' },
  { value: 'MANUAL_UPLOAD', label: 'Manual Upload' },
];

const CONNECTION_TYPES: SourceType[] = ['KAFKA', 'DB_POLL', 'FILE_SHARE'];

function ConnectionDropdown({ sourceType }: { sourceType: SourceType }) {
  const { sourceConnectionId, setSourceConnectionId } = useWizardStore();
  const { data: connections = [], isLoading } = useSourceConnections(sourceType);

  if (isLoading) return <p className="form-hint">Loading connections…</p>;
  if (connections.length === 0) return <p className="form-hint">No connections configured for this source type.</p>;

  return (
    <div className="form-field">
      <label htmlFor="sourceConnection" className="form-label">
        Connection <span className="required">*</span>
      </label>
      <select
        id="sourceConnection"
        className="form-input"
        value={sourceConnectionId ?? ''}
        onChange={e => setSourceConnectionId(e.target.value || null)}
      >
        <option value="">— select a connection —</option>
        {connections.map(c => (
          <option key={c.id} value={c.id}>{c.displayName}</option>
        ))}
      </select>
    </div>
  );
}

export function StepSourceConfig() {
  const { sourceType, setSourceType, setSourceConnectionId } = useWizardStore();

  function handleTypeChange(type: SourceType) {
    setSourceType(type);
    setSourceConnectionId(null);
  }

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Source Configuration</h2>

      <div className="form-field">
        <p className="form-label">Source Type <span className="required">*</span></p>
        <div className="radio-group">
          {SOURCE_TYPES.map(({ value, label }) => (
            <label key={value} className="radio-option">
              <input
                type="radio"
                name="sourceType"
                value={value}
                checked={sourceType === value}
                onChange={() => handleTypeChange(value)}
              />
              {label}
            </label>
          ))}
        </div>
      </div>

      {sourceType && CONNECTION_TYPES.includes(sourceType) && (
        <ConnectionDropdown sourceType={sourceType} />
      )}
    </div>
  );
}
