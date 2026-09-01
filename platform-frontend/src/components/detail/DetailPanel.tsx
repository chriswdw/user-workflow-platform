import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { useWorkItem } from '../../api/useWorkItem';
import { useAuditTrail } from '../../api/useAuditTrail';
import { useDetailViewConfig } from '../../api/useDetailViewConfig';
import { useTransition } from '../../api/useTransition';
import { useAuthStore } from '../../store/authStore';
import { DetailView } from './DetailView';
import { AuditTrail } from '../audit/AuditTrail';

interface DetailPanelProps {
  readonly workItemId: string;
  readonly onClose: () => void;
}

type Tab = 'details' | 'audit';

const DETAIL_SKELETON_BLOCKS = Array.from({ length: 6 }, (_, i) => ({
  id: `detail-skeleton-${i}`,
  width: i % 2 === 0 ? '70%' : '50%',
}));

export function DetailPanel({ workItemId, onClose }: DetailPanelProps) {
  const [tab, setTab] = useState<Tab>('details');

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);
  const userRole = useAuthStore(s => s.role) ?? '';

  const { data: item, isLoading: itemLoading, isError: itemError } = useWorkItem(workItemId);
  const { data: config, isLoading: configLoading } = useDetailViewConfig(item?.workflowType ?? '');
  const { data: auditEntries = [] } = useAuditTrail(workItemId);
  const { mutate: fireTransition, error: transitionError, isPending: transitionPending } = useTransition(workItemId);

  const loading = itemLoading || configLoading;

  const handleTransition = (transition: string, additionalFields?: Record<string, unknown>) => {
    fireTransition(
      { transition, additionalFields },
      { onSuccess: () => toast.success(`Transition "${transition}" applied.`) }
    );
  };

  return (
    <div className="detail-panel">
      <header className="detail-panel-header">
        {item && (
          <>
            <span className="detail-panel-id">{item.id}</span>
            <span className={`badge badge--${item.status.toLowerCase().replaceAll('_', '-')}`}>
              {item.status.replaceAll('_', ' ')}
            </span>
            {item.priorityLevel && (
              <span className={`badge badge--priority-${item.priorityLevel.toLowerCase()}`}>
                {item.priorityLevel}
              </span>
            )}
          </>
        )}
        <button type="button" className="detail-panel-close" onClick={onClose} aria-label="Close">✕</button>
      </header>

      <div className="detail-panel-tabs" role="tablist" aria-label="Work item details">
        <button
          type="button"
          role="tab"
          id="tab-details"
          aria-selected={tab === 'details'}
          aria-controls="panel-details"
          className={tab === 'details' ? 'tab tab--active' : 'tab'}
          onClick={() => setTab('details')}
        >
          Details
        </button>
        <button
          type="button"
          role="tab"
          id="tab-audit"
          aria-selected={tab === 'audit'}
          aria-controls="panel-audit"
          className={tab === 'audit' ? 'tab tab--active' : 'tab'}
          onClick={() => setTab('audit')}
        >
          Audit Trail
        </button>
      </div>

      <div
        role="tabpanel"
        id="panel-details"
        aria-labelledby="tab-details"
        className="detail-panel-body"
        hidden={tab !== 'details'}
      >
        {loading && (
          <div role="status" aria-live="polite" aria-label="Loading item details" className="skeleton-container">
            {DETAIL_SKELETON_BLOCKS.map(block => (
              <div key={block.id} className="skeleton skeleton-block" style={{ width: block.width }} />
            ))}
          </div>
        )}
        {itemError && (
          <div role="alert" className="status-text status-text--error" style={{ padding: '24px' }}>Failed to load item.</div>
        )}
        {!loading && item && config && (
          <DetailView
            config={config}
            item={item}
            userRole={userRole}
            onTransition={handleTransition}
            transitionError={transitionError?.message}
            transitionPending={transitionPending}
          />
        )}
      </div>

      <div
        role="tabpanel"
        id="panel-audit"
        aria-labelledby="tab-audit"
        className="detail-panel-body"
        hidden={tab !== 'audit'}
      >
        <AuditTrail entries={auditEntries} />
      </div>
    </div>
  );
}
