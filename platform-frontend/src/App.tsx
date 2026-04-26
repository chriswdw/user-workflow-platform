import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './store/authStore';
import { LoginPage } from './components/LoginPage';
import { Blotter } from './components/blotter/Blotter';
import { DetailPanel } from './components/detail/DetailPanel';
import { useWorkItems } from './api/useWorkItems';
import { BLOTTER_CONFIGS, WORKFLOW_TYPES } from './config/blotterConfigs';

const queryClient = new QueryClient();

function MainApp() {
  const { role, userId, clearAuth } = useAuthStore();
  const [workflowType, setWorkflowType] = useState(WORKFLOW_TYPES[0]);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const { data: items = [], isLoading, isError } = useWorkItems(workflowType);
  const config = BLOTTER_CONFIGS[workflowType];

  return (
    <div>
      <header className="app-header">
        <span className="app-header-title">Workflow Platform</span>
        <select
          className="app-header-select"
          value={workflowType}
          onChange={e => setWorkflowType(e.target.value)}
        >
          {WORKFLOW_TYPES.map(t => (
            <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <span className="app-header-user">{userId} · {role}</span>
        <button className="app-header-logout" onClick={clearAuth}>Logout</button>
      </header>

      <main className="app-main">
        {isLoading && <p style={{ color: 'var(--color-text-muted)' }}>Loading…</p>}
        {isError && <p style={{ color: 'var(--color-danger)' }}>Failed to load work items.</p>}
        {!isLoading && !isError && config && (
          <div className="blotter-container">
            <Blotter
              config={config}
              items={items}
              userRole={role ?? ''}
              onSelectItem={setSelectedItemId}
            />
          </div>
        )}
      </main>

      {selectedItemId && (
        <DetailPanel
          workItemId={selectedItemId}
          onClose={() => setSelectedItemId(null)}
        />
      )}
    </div>
  );
}

export function App() {
  const token = useAuthStore(s => s.token);

  return (
    <QueryClientProvider client={queryClient}>
      {token ? <MainApp /> : <LoginPage />}
    </QueryClientProvider>
  );
}
