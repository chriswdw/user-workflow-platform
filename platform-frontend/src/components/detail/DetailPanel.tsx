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
    <div className="detail-panel">
      <header className="detail-panel-header">
        {item && (
          <>
            <span className="detail-panel-id">{item.id}</span>
            <span className={`badge badge--${item.status.toLowerCase().replace(/_/g, '-')}`}>
              {item.status.replace(/_/g, ' ')}
            </span>
            {item.priorityLevel && (
              <span className={`badge badge--priority-${item.priorityLevel.toLowerCase()}`}>
                {item.priorityLevel}
              </span>
            )}
          </>
        )}
        <button className="detail-panel-close" onClick={onClose} aria-label="Close">×</button>
      </header>

      <nav className="detail-panel-tabs">
        <button className={tab === 'details' ? 'tab tab--active' : 'tab'} onClick={() => setTab('details')}>
          Details
        </button>
        <button className={tab === 'audit' ? 'tab tab--active' : 'tab'} onClick={() => setTab('audit')}>
          Audit Trail
        </button>
      </nav>

      <div className="detail-panel-body">
        {loading && <p style={{ padding: 24, color: 'var(--color-text-muted)' }}>Loading…</p>}
        {itemError && <p style={{ padding: 24, color: 'var(--color-danger)' }}>Failed to load item.</p>}

        {!loading && item && config && tab === 'details' && (
          <DetailView
            config={config}
            item={item}
            userRole={userRole}
            onTransition={handleTransition}
            transitionError={transitionError?.message}
          />
        )}

        {tab === 'audit' && <AuditTrail entries={auditEntries} />}
      </div>
    </div>
  );
}
