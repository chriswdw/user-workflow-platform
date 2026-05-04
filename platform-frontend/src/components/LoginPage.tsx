import { useDevLogin } from '../api/useDevLogin';

const ROLES = [
  { label: 'Analyst',        userId: 'analyst-1',    role: 'ANALYST',         tenantId: 'tenant-1' },
  { label: 'Supervisor',     userId: 'supervisor-1', role: 'SUPERVISOR',      tenantId: 'tenant-1' },
  { label: 'Read Only',      userId: 'readonly-1',   role: 'READ_ONLY',       tenantId: 'tenant-1' },
  { label: 'Administrator',  userId: 'admin-1',      role: 'PLATFORM_ADMIN',  tenantId: 'tenant-1' },
];

export function LoginPage() {
  const login = useDevLogin();

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" stroke="#fff" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </div>
        <h1 className="login-title">Workflow Platform</h1>
        <p className="login-subtitle">Select a role to sign in</p>
        <div className="login-divider" />
        {ROLES.map(r => (
          <button
            key={r.role}
            className="login-btn"
            onClick={() => login.mutate(r)}
            disabled={login.isPending}
          >
            {login.isPending ? 'Signing in…' : `Continue as ${r.label}`}
          </button>
        ))}
        {login.isError && (
          <p className="login-error">Login failed — is the backend running?</p>
        )}
      </div>
    </div>
  );
}
