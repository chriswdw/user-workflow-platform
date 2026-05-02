import { useWizardStore, type FieldMappingRow } from '../../store/wizardStore';

const FIELD_TYPES: FieldMappingRow['type'][] = ['STRING', 'DECIMAL', 'DATE', 'BOOLEAN'];

export function StepFieldMapping() {
  const { sampleFields, fieldMappings, idempotencyKeyField, setFieldMappings, setIdempotencyKeyField } =
    useWizardStore();

  const rows: FieldMappingRow[] =
    fieldMappings.length > 0
      ? fieldMappings
      : sampleFields.map(f => ({ fieldPath: f, displayName: f, type: 'STRING', required: false }));

  function updateRow(index: number, patch: Partial<FieldMappingRow>) {
    const updated = rows.map((r, i) => (i === index ? { ...r, ...patch } : r));
    setFieldMappings(updated);
  }

  function addRow() {
    setFieldMappings([...rows, { fieldPath: '', displayName: '', type: 'STRING', required: false }]);
  }

  function removeRow(index: number) {
    const updated = rows.filter((_, i) => i !== index);
    setFieldMappings(updated);
    if (idempotencyKeyField === rows[index]?.fieldPath) {
      setIdempotencyKeyField(null);
    }
  }

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Field Mapping</h2>
      <p className="form-hint">Map source fields to target paths in the work item. Exactly one field must be the idempotency key.</p>

      <table className="mapping-table">
        <thead>
          <tr>
            <th>Source Field</th>
            <th>Target Path</th>
            <th>Type</th>
            <th>Required</th>
            <th>Idempotency Key</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i}>
              <td>
                {sampleFields.length > 0 ? (
                  <span className="field-tag">{sampleFields[i] ?? row.fieldPath}</span>
                ) : (
                  <input
                    type="text"
                    className="form-input form-input--sm"
                    value={row.fieldPath}
                    onChange={e => updateRow(i, { fieldPath: e.target.value })}
                    placeholder="source.field"
                  />
                )}
              </td>
              <td>
                <input
                  type="text"
                  className="form-input form-input--sm"
                  value={row.fieldPath}
                  onChange={e => updateRow(i, { fieldPath: e.target.value })}
                  placeholder="target.path"
                />
              </td>
              <td>
                <select
                  className="form-input form-input--sm"
                  value={row.type}
                  onChange={e => updateRow(i, { type: e.target.value as FieldMappingRow['type'] })}
                >
                  {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </td>
              <td className="cell-center">
                <input
                  type="checkbox"
                  checked={row.required}
                  onChange={e => updateRow(i, { required: e.target.checked })}
                />
              </td>
              <td className="cell-center">
                <input
                  type="radio"
                  name="idempotencyKey"
                  checked={idempotencyKeyField === row.fieldPath}
                  onChange={() => setIdempotencyKeyField(row.fieldPath)}
                />
              </td>
              <td>
                <button
                  type="button"
                  className="btn btn-danger btn-xs"
                  onClick={() => removeRow(i)}
                >
                  ✕
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <button type="button" className="btn btn-secondary" onClick={addRow}>
        + Add field
      </button>
    </div>
  );
}
