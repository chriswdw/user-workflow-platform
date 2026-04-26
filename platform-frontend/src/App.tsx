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
    <div style={{ fontFamily: 'sans-serif' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '8px 16px', background: '#1a1a2e', color: 'white' }}>
        <strong style={{ fontSize: 18 }}>Workflow Platform</strong>
        <select
          value={workflowType}
          onChange={e => setWorkflowType(e.target.value)}
          style={{ marginLeft: 16, padding: '4px 8px' }}
        >
          {WORKFLOW_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
        </select>
        <span style={{ marginLeft: 'auto', fontSize: 13, opacity: 0.8 }}>
          {userId} · {role}
        </span>
        <button onClick={clearAuth} style={{ padding: '4px 12px', cursor: 'pointer' }}>
          Logout
        </button>
      </header>

      <main style={{ padding: 16 }}>
        {isLoading && <p>Loading…</p>}
        {isError  && <p style={{ color: 'red' }}>Failed to load work items.</p>}
        {!isLoading && !isError && config && (
          <Blotter
            config={config}
            items={items}
            userRole={role ?? ''}
            onSelectItem={setSelectedItemId}
          />
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
