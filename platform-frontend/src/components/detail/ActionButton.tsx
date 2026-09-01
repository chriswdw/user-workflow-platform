import { useEffect, useRef, useState } from 'react';
import type { Action } from '../../types/DetailViewConfig';
import { ConfirmModal } from './ConfirmModal';
import { ActionFormModal } from './ActionFormModal';

interface ActionButtonProps {
  readonly action: Action;
  readonly onTransition: (transition: string, additionalFields?: Record<string, unknown>) => void;
  readonly serverError?: string;
  readonly transitionPending?: boolean;
}

const styleClass: Record<Action['style'], string> = {
  PRIMARY: 'btn-primary',
  SECONDARY: 'btn-secondary',
  DANGER: 'btn-danger',
};

export function ActionButton({ action, onTransition, serverError, transitionPending }: ActionButtonProps) {
  const [open, setOpen] = useState(false);
  const hasInputFields = (action.inputFields ?? []).length > 0;
  const needsModal = hasInputFields || action.confirmationRequired;

  // A submitted transition closes its modal only once it actually settles, and only if it
  // succeeded (no serverError) — closing synchronously on submit (the previous behaviour) meant
  // the modal unmounted before the mutation's error could ever reach the screen, so a failed
  // transition failed silently. `wasPending` tracks the mutation's own pending flag across
  // renders to detect the false→true→false transition of one attempt settling.
  const wasPending = useRef(false);
  useEffect(() => {
    if (wasPending.current && !transitionPending) {
      if (!serverError) setOpen(false);
    }
    wasPending.current = Boolean(transitionPending);
  }, [transitionPending, serverError]);

  const handleClick = () => {
    if (!needsModal) {
      onTransition(action.transition);
      return;
    }
    setOpen(true);
  };

  const handleConfirm = () => {
    onTransition(action.transition);
  };

  const handleFormSubmit = (values: Record<string, unknown>) => {
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
          serverError={serverError}
        />
      )}
    </>
  );
}
