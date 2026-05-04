import type { MouseEventHandler } from 'react';

interface ConfirmModalProps {
  readonly message: string;
  readonly onConfirm: MouseEventHandler<HTMLButtonElement>;
  readonly onCancel: MouseEventHandler<HTMLButtonElement>;
}

export function ConfirmModal({ message, onConfirm, onCancel }: ConfirmModalProps) {
  return (
    <dialog open className="modal-overlay" aria-modal="true">
      <div className="modal-box">
        <p>{message}</p>
        <div className="modal-actions">
          <button className="btn-secondary" onClick={onCancel}>Cancel</button>
          <button className="btn-primary" onClick={onConfirm}>Confirm</button>
        </div>
      </div>
    </dialog>
  );
}
