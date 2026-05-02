import { useWizardStore, type DetailSectionDraft } from '../../store/wizardStore';

const LAYOUTS: DetailSectionDraft['layout'][] = ['TWO_COLUMN', 'ONE_COLUMN', 'TABLE'];
const FORMATTERS = ['', 'CURRENCY', 'DATE', 'DATETIME', 'PERCENTAGE', 'BADGE', 'TEXT'];

export function StepDetailViewConfig() {
  const { fieldMappings, detailSections, setDetailSections } = useWizardStore();
  const targetPaths = fieldMappings.map(m => m.fieldPath).filter(Boolean);

  function addSection() {
    setDetailSections([
      ...detailSections,
      { sectionName: `Section ${detailSections.length + 1}`, layout: 'TWO_COLUMN', collapsible: false, fields: [] },
    ]);
  }

  function removeSection(index: number) {
    setDetailSections(detailSections.filter((_, i) => i !== index));
  }

  function updateSection(index: number, patch: Partial<DetailSectionDraft>) {
    setDetailSections(detailSections.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  }

  function toggleField(sectionIndex: number, path: string, checked: boolean) {
    const section = detailSections[sectionIndex];
    if (!section) return;
    const fields = checked
      ? [...section.fields, { fieldPath: path, label: path }]
      : section.fields.filter(f => f.fieldPath !== path);
    updateSection(sectionIndex, { fields });
  }

  function updateFieldLabel(sectionIndex: number, fieldPath: string, label: string) {
    const section = detailSections[sectionIndex];
    if (!section) return;
    const fields = section.fields.map(f => (f.fieldPath === fieldPath ? { ...f, label } : f));
    updateSection(sectionIndex, { fields });
  }

  function updateFieldFormatter(sectionIndex: number, fieldPath: string, formatter: string) {
    const section = detailSections[sectionIndex];
    if (!section) return;
    const fields = section.fields.map(f =>
      f.fieldPath === fieldPath ? { ...f, formatter: formatter || undefined } : f
    );
    updateSection(sectionIndex, { fields });
  }

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Detail View Configuration</h2>
      <p className="form-hint">Define sections that appear in the work item detail panel.</p>

      {detailSections.map((section, si) => (
        <div key={si} className="detail-section-card">
          <div className="detail-section-header">
            <input
              type="text"
              className="form-input form-input--sm"
              value={section.sectionName}
              onChange={e => updateSection(si, { sectionName: e.target.value })}
              placeholder="Section title"
            />
            <div className="radio-group radio-group--inline">
              {LAYOUTS.map(layout => (
                <label key={layout} className="radio-option">
                  <input
                    type="radio"
                    name={`layout-${si}`}
                    value={layout}
                    checked={section.layout === layout}
                    onChange={() => updateSection(si, { layout })}
                  />
                  {layout}
                </label>
              ))}
            </div>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={section.collapsible ?? false}
                onChange={e => updateSection(si, { collapsible: e.target.checked })}
              />
              Collapsible
            </label>
            <button
              type="button"
              className="btn btn-danger btn-xs"
              onClick={() => removeSection(si)}
            >
              Remove section
            </button>
          </div>

          <div className="detail-section-fields">
            {targetPaths.map(path => {
              const assigned = section.fields.find(f => f.fieldPath === path);
              return (
                <div key={path} className="field-assign-row">
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={!!assigned}
                      onChange={e => toggleField(si, path, e.target.checked)}
                    />
                    <code>{path}</code>
                  </label>
                  {assigned && (
                    <>
                      <input
                        type="text"
                        className="form-input form-input--sm"
                        placeholder="Label"
                        value={assigned.label}
                        onChange={e => updateFieldLabel(si, path, e.target.value)}
                      />
                      <select
                        className="form-input form-input--sm"
                        value={assigned.formatter ?? ''}
                        onChange={e => updateFieldFormatter(si, path, e.target.value)}
                      >
                        {FORMATTERS.map(f => <option key={f} value={f}>{f || '— none —'}</option>)}
                      </select>
                    </>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}

      <button type="button" className="btn btn-secondary" onClick={addSection}>
        + Add section
      </button>
    </div>
  );
}
