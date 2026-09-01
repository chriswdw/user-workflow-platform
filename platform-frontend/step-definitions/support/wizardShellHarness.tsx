import type { RenderResult } from '@testing-library/react';
import { WizardShell } from '../../src/components/wizard/WizardShell';
import { renderWithProviders } from './render';

/**
 * Shared render/close state for WizardShell scenarios split across multiple step-definition
 * files (wizard-shell-component.steps.tsx for pure navigation, wizard-shell-submission-flow.
 * steps.tsx for the create/save/submit/revise mutation flows) — both need "When I render the
 * wizard shell" and "Then the wizard shell onClose callback fires" to refer to the SAME render,
 * which a module-private variable per file can't provide since cucumber step definitions are
 * matched globally by pattern, not per file.
 */
let rendered: RenderResult | null = null;
let closed = false;

export function renderWizardShell(): RenderResult {
  closed = false;
  rendered = renderWithProviders(<WizardShell onClose={() => { closed = true; }} />);
  return rendered;
}

export function getRenderedWizardShell(): RenderResult {
  if (!rendered) throw new Error('the wizard shell has not been rendered yet in this scenario');
  return rendered;
}

export function wasWizardShellClosed(): boolean {
  return closed;
}
