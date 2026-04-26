import { useDevLogin } from '../api/useDevLogin';

const ROLES = [
  { label: 'Analyst',    userId: 'analyst-1',    role: 'ANALYST',    tenantId: 'tenant-1' },
  { label: 'Supervisor', userId: 'supervisor-1', role: 'SUPERVISOR', tenantId: 'tenant-1' },
  { label: 'Read Only',  userId: 'readonly-1',   role: 'READ_ONLY',  tenantId: 'tenant-1' },
];

export function LoginPage() {
  const login = useDevLogin();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', marginTop: 120, gap: 16 }}>
      <h1 style={{ marginBottom: 8 }}>Workflow Platform</h1>
      <p style={{ color: '#666', marginBottom: 24 }}>Dev login — select a role to continue</p>
      {ROLES.map(r => (
        <button
          key={r.role}
          onClick={() => login.mutate(r)}
          disabled={login.isPending}
          style={{ width: 220, padding: '10px 0', fontSize: 15, cursor: 'pointer' }}
        >
          Login as {r.label}
        </button>
      ))}
      {login.isError && (
        <p style={{ color: 'red' }}>Login failed — is the backend running?</p>
      )}
    </div>
  );
}
