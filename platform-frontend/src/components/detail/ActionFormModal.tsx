import { useForm } from 'react-hook-form';
import type { ActionInputField } from '../../types/DetailViewConfig';

interface ActionFormModalProps {
  readonly label: string;
  readonly inputFields: ActionInputField[];
  readonly onSubmit: (values: Record<string, unknown>) => void;
  readonly onCancel: () => void;
  readonly serverError?: string;
}

interface FieldInputProps {
  readonly f: ActionInputField;
  readonly register: ReturnType<typeof useForm<Record<string, string>>>['register'];
}

function FieldInput({ f, register }: FieldInputProps) {
  const validation = { required: f.required ? `${f.label} is required` : false };

  if (f.inputType === 'TEXTAREA') {
    return <textarea id={f.field} {...register(f.field, validation)} />;
  }

  if (f.inputType === 'SELECT') {
    return (
      <select id={f.field} {...register(f.field, validation)}>
        <option value="">— select —</option>
        {(f.options ?? []).map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    );
  }

  const inputType = f.inputType === 'DATE' ? 'date' : 'text';
  const currencyPattern = f.inputType === 'CURRENCY'
    ? { value: /^\d+(\.\d{1,2})?$/, message: 'Enter a valid amount' }
    : undefined;

  return (
    <input
      id={f.field}
      type={inputType}
      inputMode={f.inputType === 'CURRENCY' ? 'decimal' : undefined}
      {...register(f.field, { ...validation, pattern: currencyPattern })}
    />
  );
}

export function ActionFormModal({ label, inputFields, onSubmit, onCancel, serverError }: ActionFormModalProps) {
  const { register, handleSubmit, formState: { errors } } = useForm<Record<string, string>>();

  return (
    <dialog open className="modal-overlay" aria-modal="true">
      <div className="modal-box">
        <h3>{label}</h3>
        {serverError && <p className="form-error">{serverError}</p>}
        <form onSubmit={handleSubmit(values => onSubmit(values))}>
          {inputFields.map(f => (
            <div key={f.field} className="form-field">
              <label htmlFor={f.field}>
                {f.label}{f.required && ' *'}
              </label>
              <FieldInput f={f} register={register} />
              {errors[f.field] && (
                <span className="field-error">{errors[f.field]?.message}</span>
              )}
            </div>
          ))}
          <div className="modal-actions">
            <button type="button" className="btn-secondary" onClick={onCancel}>Cancel</button>
            <button type="submit" className="btn-primary">Submit</button>
          </div>
        </form>
      </div>
    </dialog>
  );
}
