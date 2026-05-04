import { Fragment, useState } from 'react';
import axios from 'axios';
import { useWizardStore } from '../../store/wizardStore';
import { isStepComplete } from '../../utils/wizardValidation';
import { useCreateSubmission } from '../../api/useCreateSubmission';
import { useSaveDraft, useSubmitForApproval, useReviseSubmission } from '../../api/useSubmissionActions';
import { StepBasicInfo } from './StepBasicInfo';
import { StepSourceConfig } from './StepSourceConfig';
import { StepSampleUpload } from './StepSampleUpload';
import { StepFieldMapping } from './StepFieldMapping';
import { StepBlotterConfig } from './StepBlotterConfig';
import { StepDetailViewConfig } from './StepDetailViewConfig';
import { StepReview } from './StepReview';

const STEPS = [
  'Basic Info',
  'Source',
  'Sample',
  'Fields',
  'Blotter',
  'Detail View',
  'Review',
];

const TOTAL_STEPS = STEPS.length;

interface Props {
  readonly onClose: () => void;
}

export function WizardShell({ onClose }: Props) {
  const store = useWizardStore();
  const { currentStep, setStep, submissionId, revisingSubmissionId, buildSubmissionPayload } = store;

  const [submitError, setSubmitError] = useState<string | null>(null);

  const createSubmission = useCreateSubmission();
  const saveDraft = useSaveDraft(submissionId ?? '');
  const submitForApproval = useSubmitForApproval(submissionId ?? '');
  const reviseSubmission = useReviseSubmission(revisingSubmissionId ?? '');

  const isSaving =
    createSubmission.isPending ||
    saveDraft.isPending ||
    submitForApproval.isPending ||
    reviseSubmission.isPending;

  const canAdvance = !isSaving && isStepComplete(currentStep, store);

  async function handleNext() {
    setSubmitError(null);

    if (currentStep === 1) {
      const payload = buildSubmissionPayload();
      createSubmission.mutate(payload, {
        onSuccess: saved => {
          useWizardStore.setState({ submissionId: saved.id });
          setStep(2);
        },
        onError: err => {
          const is409 = axios.isAxiosError(err) && err.response?.status === 409;
          setSubmitError(
            is409
              ? `A submission for "${store.workflowType}" already exists. Open My Submissions to continue it.`
              : (err.message ?? 'Failed to create submission.')
          );
        },
      });
      return;
    }

    if (currentStep >= 2 && currentStep <= 6 && submissionId) {
      const payload = buildSubmissionPayload();
      saveDraft.mutate(
        { draftConfigs: payload as unknown as Record<string, unknown>, currentStep },
        {
          onSuccess: () => setStep(currentStep + 1),
          onError: err => setSubmitError(err.message ?? 'Failed to save draft.'),
        }
      );
      return;
    }

    setStep(currentStep + 1);
  }

  async function handleSubmit() {
    setSubmitError(null);
    if (!submissionId) return;

    const doSubmit = () => {
      submitForApproval.mutate(undefined, {
        onSuccess: onClose,
        onError: err => {
          const msg = err.message ?? 'Submission failed.';
          const match = /incomplete.*step\s*(\d+)/i.exec(msg);
          setSubmitError(match ? `Incomplete — return to step ${match[1]}` : msg);
        },
      });
    };

    if (revisingSubmissionId) {
      reviseSubmission.mutate(undefined, { onSuccess: doSubmit });
    } else {
      doSubmit();
    }
  }

  function renderStep() {
    switch (currentStep) {
      case 1: return <StepBasicInfo />;
      case 2: return <StepSourceConfig />;
      case 3: return <StepSampleUpload />;
      case 4: return <StepFieldMapping />;
      case 5: return <StepBlotterConfig />;
      case 6: return <StepDetailViewConfig />;
      case 7: return <StepReview submitError={submitError} onGoToStep={setStep} />;
      default: return null;
    }
  }

  return (
    <div className="wizard-overlay">
      <div className="wizard-modal">
        <div className="wizard-header">
          <h1 className="wizard-title">Create Workflow Type</h1>
          <button type="button" className="btn btn-icon" onClick={onClose} aria-label="Close">✕</button>
        </div>

        <nav className="wizard-progress" aria-label="Wizard steps">
          {STEPS.map((label, i) => {
            const stepNum = i + 1;
            const status =
              stepNum < currentStep ? 'done' :
              stepNum === currentStep ? 'active' : 'pending';
            return (
              <Fragment key={stepNum}>
                <div className={`wizard-progress-step wizard-progress-step--${status}`}>
                  <span className="wizard-progress-circle">
                    {status === 'done' ? '✓' : stepNum}
                  </span>
                  <span className="wizard-progress-label">{label}</span>
                </div>
                {i < STEPS.length - 1 && (
                  <div className={`wizard-progress-connector wizard-progress-connector--${status === 'done' ? 'done' : 'pending'}`} />
                )}
              </Fragment>
            );
          })}
        </nav>

        <div className="wizard-body">
          {renderStep()}
        </div>

        <div className="wizard-footer">
          {isSaving && <span className="saving-indicator">Saving…</span>}
          {submitError && !isSaving && <span className="wizard-footer-error">{submitError}</span>}

          <div className="wizard-footer-nav">
            {currentStep > 1 && (
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setStep(currentStep - 1)}
                disabled={isSaving}
              >
                ← Prev
              </button>
            )}

            {currentStep < TOTAL_STEPS && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void handleNext()}
                disabled={!canAdvance}
              >
                Next →
              </button>
            )}

            {currentStep === TOTAL_STEPS && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => void handleSubmit()}
                disabled={isSaving}
              >
                Submit for Approval
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
