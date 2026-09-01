import { render, screen } from '@testing-library/react';
import { App } from '../App';
import { useAuthStore } from '../store/authStore';

// Stub every component that has its own hook dependencies
jest.mock('../components/LoginPage', () => ({ LoginPage: () => <div>Login</div> }));
jest.mock('../components/blotter/Blotter', () => ({ Blotter: () => null }));
jest.mock('../components/detail/DetailPanel', () => ({ DetailPanel: () => null }));
jest.mock('../components/wizard/WizardShell', () => ({ WizardShell: () => null }));
jest.mock('../components/wizard/ApprovalQueue', () => ({ ApprovalQueue: () => null }));
jest.mock('../components/wizard/MySubmissionsView', () => ({ MySubmissionsView: () => null }));
jest.mock('../components/admin/SourceConnectionsAdminView', () => ({ SourceConnectionsAdminView: () => null }));
jest.mock('../components/admin/AllDraftsAdminView', () => ({ AllDraftsAdminView: () => null }));

jest.mock('../api/useWorkItems', () => ({
  useWorkItems: () => ({ data: [], isLoading: false, isError: false }),
}));
jest.mock('../api/useWorkflowTypeSubmissions', () => ({
  usePendingSubmissions: () => ({ data: [] }),
  useMyDraftSubmissions: () => ({ data: [] }),
  useMyRejectedSubmissions: () => ({ data: [] }),
}));

function setAuth(role: string) {
  useAuthStore.setState({ token: 'tok', userId: 'u1', role, tenantId: 'tenant-1' });
}

afterEach(() => {
  useAuthStore.setState({ token: null, userId: null, role: null, tenantId: null });
});

describe('App nav — role-based visibility', () => {
  it('shows the login page when unauthenticated', () => {
    render(<App />);
    expect(screen.getByText('Login')).toBeInTheDocument();
  });

  it('shows standard nav buttons for ANALYST role', () => {
    setAuth('ANALYST');
    render(<App />);

    expect(screen.getByRole('button', { name: 'Blotter' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create Workflow Type' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /pending approvals/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /my submissions/i })).toBeInTheDocument();
  });

  it('does not show admin buttons for non-admin users', () => {
    setAuth('ANALYST');
    render(<App />);

    expect(screen.queryByRole('button', { name: 'Source Connections' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'All Drafts' })).not.toBeInTheDocument();
  });

  it('shows Source Connections and All Drafts buttons for PLATFORM_ADMIN', () => {
    setAuth('PLATFORM_ADMIN');
    render(<App />);

    expect(screen.getByRole('button', { name: 'Source Connections' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'All Drafts' })).toBeInTheDocument();
  });

  it('shows the logged-in user identity in the header', () => {
    setAuth('ANALYST');
    render(<App />);

    expect(screen.getByText(/u1/)).toBeInTheDocument();
    expect(screen.getByText(/ANALYST/)).toBeInTheDocument();
  });
});
