import { useRef } from 'react';
import { useWizardStore } from '../../store/wizardStore';
import { extractFieldPaths, extractCsvHeaders, isSupportedFileType } from '../../utils/fieldExtractor';

const MAX_FILE_SIZE = 1024 * 1024; // 1 MB

export function StepSampleUpload() {
  const { sampleFields, setSampleFields } = useWizardStore();
  const inputRef = useRef<HTMLInputElement>(null);

  if (sampleFields.length > 0) {
    return (
      <div className="wizard-step">
        <h2 className="wizard-step-title">Sample Data</h2>
        <div className="info-banner">
          Fields pre-loaded from saved draft ({sampleFields.length} fields). You can proceed to the next step or re-upload to replace them.
        </div>
        <ul className="field-list">
          {sampleFields.map(f => <li key={f} className="field-list-item">{f}</li>)}
        </ul>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => setSampleFields([])}
        >
          Clear and re-upload
        </button>
      </div>
    );
  }

  async function handleFile(file: File) {
    if (!isSupportedFileType(file.name)) {
      alert('Only .json and .csv files are supported.');
      return;
    }
    if (file.size > MAX_FILE_SIZE) {
      alert('File exceeds the 1 MB limit.');
      return;
    }
    const text = await file.text();
    let fields: string[];
    if (file.name.endsWith('.json')) {
      try {
        const obj = JSON.parse(text) as Record<string, unknown>;
        fields = extractFieldPaths(Array.isArray(obj) ? (obj[0] as Record<string, unknown>) : obj);
      } catch {
        alert('Invalid JSON file.');
        return;
      }
    } else {
      fields = extractCsvHeaders(text);
    }
    setSampleFields(fields);
  }

  return (
    <div className="wizard-step">
      <h2 className="wizard-step-title">Sample Data</h2>
      <p className="form-hint">
        Upload a sample .json or .csv file (max 1 MB) so we can detect field names automatically.
      </p>

      <input
        ref={inputRef}
        type="file"
        accept=".json,.csv"
        style={{ display: 'none' }}
        onChange={e => {
          const file = e.target.files?.[0];
          if (file) void handleFile(file);
          e.target.value = '';
        }}
      />

      <div className="button-row">
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => inputRef.current?.click()}
        >
          Upload sample file
        </button>
        <span className="button-row-separator">or</span>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => setSampleFields([])}
        >
          Define fields manually
        </button>
      </div>
    </div>
  );
}
