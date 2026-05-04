import { useState } from 'react';
import type { Action } from '../../types/DetailViewConfig';
import { ConfirmModal } from './ConfirmModal';
import { ActionFormModal } from './ActionFormModal';

interface ActionButtonProps {
  readonly action: Action;
  readonly onTransition: (transition: string, additionalFields?: Record<string, unknown>) => void;
  readonly serverError?: string;
}

const styleClass: Record<Action['style'], string> = {
  PRIMARY: 'btn-primary',
  SECONDARY: 'btn-secondary',
  DANGER: 'btn-danger',
};

export function ActionButton({ action, onTransition, serverError }: ActionButtonProps) {
  const [open, setOpen] = useState(false);
  const hasInputFields = (action.inputFields ?? []).length > 0;
  const needsModal = hasInputFields || action.confirmationRequired;

  const handleClick = () => {
    if (!needsModal) {
      onTransition(action.transition);
      return;
    }
    setOpen(true);
  };

  const handleConfirm = () => {
    setOpen(false);
    onTransition(action.transition);
  };

  const handleFormSubmit = (values: Record<string, unknown>) => {
    setOpen(false);
    onTransition(action.transition, values);
  };

  const handleCancel = () => setOpen(false);

  return (
    <>
      <button className={styleClass[action.style]} onClick={handleClick}>
        {action.label}
      </button>

      {open && hasInputFields && (
        <ActionFormModal
          label={action.label}
          inputFields={action.inputFields!}
          onSubmit={handleFormSubmit}
          onCancel={handleCancel}
          serverError={serverError}
        />
      )}

      {open && !hasInputFields && action.confirmationRequired && (
        <ConfirmModal
          message={action.confirmationMessage ?? 'Confirm action?'}
          onConfirm={handleConfirm}
          onCancel={handleCancel}
        />
      )}
    </>
  );
}
