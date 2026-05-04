import { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './store/authStore';
import { useWizardStore } from './store/wizardStore';
import { LoginPage } from './components/LoginPage';
import { Blotter } from './components/blotter/Blotter';
import { DetailPanel } from './components/detail/DetailPanel';
import { WizardShell } from './components/wizard/WizardShell';
import { ApprovalQueue } from './components/wizard/ApprovalQueue';
import { MySubmissionsView } from './components/wizard/MySubmissionsView';
import { SourceConnectionsAdminView } from './components/admin/SourceConnectionsAdminView';
import { AllDraftsAdminView } from './components/admin/AllDraftsAdminView';
import { useWorkItems } from './api/useWorkItems';
import { useMyDraftSubmissions, useMyRejectedSubmissions, usePendingSubmissions } from './api/useWorkflowTypeSubmissions';
import { BLOTTER_CONFIGS, WORKFLOW_TYPES } from './config/blotterConfigs';

type AppView = 'blotter' | 'wizard' | 'approval-queue' | 'my-submissions' | 'admin-connections' | 'admin-drafts';

const queryClient = new QueryClient();

function MainApp() {
  const { role, userId, clearAuth } = useAuthStore();
  const [view, setView] = useState<AppView>('blotter');
  const [workflowType, setWorkflowType] = useState(WORKFLOW_TYPES[0]);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const { data: items = [], isLoading, isError } = useWorkItems(workflowType);
  const config = BLOTTER_CONFIGS[workflowType];
  const { reset } = useWizardStore();

  const { data: pendingApprovals = [] } = usePendingSubmissions();
  const { data: myDrafts = [] } = useMyDraftSubmissions();
  const { data: myRejected = [] } = useMyRejectedSubmissions();
  const mySubmissionCount = myDrafts.length + myRejected.length;

  function openWizard() {
    reset();
    setView('wizard');
  }

  return (
    <div>
      <header className="app-header">
        <span className="app-header-title">Workflow Platform</span>

        <nav className="app-header-nav">
          <button
            type="button"
            className={`app-header-nav-btn${view === 'blotter' ? ' app-header-nav-btn--active' : ''}`}
            onClick={() => setView('blotter')}
          >
            Blotter
          </button>

          <button
            type="button"
            className="app-header-nav-btn"
            onClick={openWizard}
          >
            Create Workflow Type
          </button>

          <button
            type="button"
            className={`app-header-nav-btn${view === 'approval-queue' ? ' app-header-nav-btn--active' : ''}`}
            onClick={() => setView('approval-queue')}
          >
            Pending Approvals{pendingApprovals.length > 0 && (
              <span className="badge badge--count">{pendingApprovals.length}</span>
            )}
          </button>

          <button
            type="button"
            className={`app-header-nav-btn${view === 'my-submissions' ? ' app-header-nav-btn--active' : ''}`}
            onClick={() => setView('my-submissions')}
          >
            My Submissions{mySubmissionCount > 0 && (
              <span className="badge badge--count">{mySubmissionCount}</span>
            )}
          </button>

          {role === 'PLATFORM_ADMIN' && (
            <button
              type="button"
              className={`app-header-nav-btn${view === 'admin-connections' ? ' app-header-nav-btn--active' : ''}`}
              onClick={() => setView('admin-connections')}
            >
              Source Connections
            </button>
          )}

          {role === 'PLATFORM_ADMIN' && (
            <button
              type="button"
              className={`app-header-nav-btn${view === 'admin-drafts' ? ' app-header-nav-btn--active' : ''}`}
              onClick={() => setView('admin-drafts')}
            >
              All Drafts
            </button>
          )}
        </nav>

        {view === 'blotter' && (
          <select
            className="app-header-select"
            value={workflowType}
            onChange={e => setWorkflowType(e.target.value)}
          >
            {WORKFLOW_TYPES.map(t => (
              <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>
            ))}
          </select>
        )}

        <span className="app-header-user">{userId} · {role}</span>
        <button className="app-header-logout" onClick={clearAuth}>Logout</button>
      </header>

      <main className="app-main">
        {view === 'blotter' && (
          <>
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
          </>
        )}

        {view === 'approval-queue' && <ApprovalQueue />}

        {view === 'my-submissions' && (
          <MySubmissionsView onOpenWizard={() => setView('wizard')} />
        )}

        {view === 'admin-connections' && <SourceConnectionsAdminView />}

        {view === 'admin-drafts' && <AllDraftsAdminView />}
      </main>

      {view === 'wizard' && <WizardShell onClose={() => setView('blotter')} />}

      {view === 'blotter' && selectedItemId && (
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
