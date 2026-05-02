import { useWizardStore, type BlotterColumnDraft } from '../../store/wizardStore';

const FORMATTERS = ['', 'CURRENCY', 'DATE', 'DATETIME', 'PERCENTAGE', 'BADGE', 'TEXT'];

export function StepBlotterConfig() {
  const { fieldMappings, blotterColumns, setBlotterColumns } = useWizardStore();
  const targetPaths = fieldMappings.map(m => m.fieldPath).filter(Boolean);

  function isSelected(path: string) {
    return blotterColumns.some(c => c.fieldPath === path);
  }

  function toggle(path: string, checked: boolean) {
    if (checked) {
      setBlotterColumns([...blotterColumns, { fieldPath: path, headerName: path }]);
    } else {
      setBlotterColumns(blotterColumns.filter(c => c.fieldPath !== path));
    }
  }

  function updateColumn(fieldPath: string, patch: Partial<BlotterColumnDraft>) {
    setBlotterColumns(blotterColumns.map(c => (c.fieldPath === fieldPath ? { ...c, ...patch } : c)));
  }

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Blotter Columns</h2>
      <p className="form-hint">Select which fields appear as columns in the blotter and configure their display.</p>

      {targetPaths.length === 0 && (
        <p className="form-hint form-hint--warn">No fields defined. Go back to Step 4 to add field mappings.</p>
      )}

      <div className="column-config-list">
        {targetPaths.map(path => {
          const col = blotterColumns.find(c => c.fieldPath === path);
          return (
            <div key={path} className="column-config-row">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={isSelected(path)}
                  onChange={e => toggle(path, e.target.checked)}
                />
                <code>{path}</code>
              </label>

              {col && (
                <div className="column-config-fields">
                  <input
                    type="text"
                    className="form-input form-input--sm"
                    placeholder="Header label"
                    value={col.headerName}
                    onChange={e => updateColumn(path, { headerName: e.target.value })}
                  />
                  <input
                    type="number"
                    className="form-input form-input--sm form-input--narrow"
                    placeholder="Width"
                    value={col.width ?? ''}
                    onChange={e => updateColumn(path, { width: e.target.value ? Number(e.target.value) : undefined })}
                  />
                  <select
                    className="form-input form-input--sm"
                    value={col.formatter ?? ''}
                    onChange={e => updateColumn(path, { formatter: e.target.value || undefined })}
                  >
                    {FORMATTERS.map(f => <option key={f} value={f}>{f || '— none —'}</option>)}
                  </select>
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={col.sortable ?? false}
                      onChange={e => updateColumn(path, { sortable: e.target.checked })}
                    />
                    Sortable
                  </label>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
