import { useState } from 'react';
import { useWorkItem } from '../../api/useWorkItem';
import { useAuditTrail } from '../../api/useAuditTrail';
import { useDetailViewConfig } from '../../api/useDetailViewConfig';
import { useTransition } from '../../api/useTransition';
import { useAuthStore } from '../../store/authStore';
import { DetailView } from './DetailView';
import { AuditTrail } from '../audit/AuditTrail';

interface Props {
  workItemId: string;
  onClose: () => void;
}

type Tab = 'details' | 'audit';

export function DetailPanel({ workItemId, onClose }: Props) {
  const [tab, setTab] = useState<Tab>('details');
  const userRole = useAuthStore(s => s.role) ?? '';

  const { data: item, isLoading: itemLoading, isError: itemError } = useWorkItem(workItemId);
  const { data: config, isLoading: configLoading } = useDetailViewConfig(item?.workflowType ?? '');
  const { data: auditEntries = [] } = useAuditTrail(workItemId);
  const { mutate: fireTransition, error: transitionError } = useTransition(workItemId);

  const loading = itemLoading || configLoading;

  const handleTransition = (transition: string, additionalFields?: Record<string, unknown>) => {
    fireTransition({ transition, additionalFields });
  };

  return (
    <div
      className="detail-panel"
      style={{
        position: 'fixed', top: 0, right: 0, height: '100vh', width: 680,
        background: '#fff', boxShadow: '-4px 0 16px rgba(0,0,0,0.15)',
        display: 'flex', flexDirection: 'column', zIndex: 100,
      }}
    >
      <header className="detail-panel-header" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', borderBottom: '1px solid #e0e0e0' }}>
        {item && (
          <>
            <span className="detail-panel-id" style={{ fontWeight: 600, fontSize: 15 }}>{item.id}</span>
            <span className={`badge badge--${item.status.toLowerCase().replace(/_/g, '-')}`}>{item.status}</span>
            {item.priorityLevel && (
              <span className={`badge badge--priority-${item.priorityLevel.toLowerCase()}`}>{item.priorityLevel}</span>
            )}
          </>
        )}
        <button
          onClick={onClose}
          style={{ marginLeft: 'auto', background: 'none', border: 'none', fontSize: 20, cursor: 'pointer' }}
          aria-label="Close"
        >
          ×
        </button>
      </header>

      <nav className="detail-panel-tabs" style={{ display: 'flex', borderBottom: '1px solid #e0e0e0' }}>
        <button
          className={tab === 'details' ? 'tab tab--active' : 'tab'}
          onClick={() => setTab('details')}
        >
          Details
        </button>
        <button
          className={tab === 'audit' ? 'tab tab--active' : 'tab'}
          onClick={() => setTab('audit')}
        >
          Audit Trail
        </button>
      </nav>

      <div className="detail-panel-body" style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
        {loading && <p>Loading…</p>}
        {itemError && <p style={{ color: 'red' }}>Failed to load item.</p>}

        {!loading && item && config && tab === 'details' && (
          <DetailView
            config={config}
            item={item}
            userRole={userRole}
            onTransition={handleTransition}
            transitionError={transitionError?.message}
          />
        )}

        {tab === 'audit' && (
          <AuditTrail entries={auditEntries} />
        )}
      </div>
    </div>
  );
}
