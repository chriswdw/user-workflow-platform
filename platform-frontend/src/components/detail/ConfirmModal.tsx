import type { MouseEventHandler } from 'react';

interface ConfirmModalProps {
  readonly message: string;
  readonly onConfirm: MouseEventHandler<HTMLButtonElement>;
  readonly onCancel: MouseEventHandler<HTMLButtonElement>;
  readonly serverError?: string;
}

export function ConfirmModal({ message, onConfirm, onCancel, serverError }: ConfirmModalProps) {
  return (
    <dialog open className="modal-overlay" aria-modal="true">
      <div className="modal-box">
        <p>{message}</p>
        {serverError && <p className="form-error">{serverError}</p>}
        <div className="modal-actions">
          <button className="btn-secondary" onClick={onCancel}>Cancel</button>
          <button className="btn-primary" onClick={onConfirm}>Confirm</button>
        </div>
      </div>
    </dialog>
  );
}
