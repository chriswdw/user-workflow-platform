import { useState } from 'react';
import { useSourceConnections } from '../../api/useSourceConnections';
import {
  useCreateSourceConnection,
  useGrantConnectionAccess,
  useRevokeConnectionAccess,
} from '../../api/useSourceConnectionAdmin';
import type { SourceConnection } from '../../types/WorkflowTypeSubmission';

type ConnectionType = 'KAFKA' | 'DB_POLL' | 'FILE_SHARE';
const CONNECTION_TYPES: ConnectionType[] = ['KAFKA', 'DB_POLL', 'FILE_SHARE'];

function KafkaFields({ config, onChange }: { config: Record<string, unknown>; onChange: (c: Record<string, unknown>) => void }) {
  return (
    <>
      <input className="form-input" placeholder="Bootstrap servers" value={(config['bootstrapServers'] as string) ?? ''} onChange={e => onChange({ ...config, bootstrapServers: e.target.value })} />
      <input className="form-input" placeholder="Topic name" value={(config['topicName'] as string) ?? ''} onChange={e => onChange({ ...config, topicName: e.target.value })} />
    </>
  );
}

function DbPollFields({ config, onChange }: { config: Record<string, unknown>; onChange: (c: Record<string, unknown>) => void }) {
  return (
    <>
      <input className="form-input" placeholder="JDBC URL" value={(config['jdbcUrl'] as string) ?? ''} onChange={e => onChange({ ...config, jdbcUrl: e.target.value })} />
      <input className="form-input" placeholder="Query" value={(config['query'] as string) ?? ''} onChange={e => onChange({ ...config, query: e.target.value })} />
      <input className="form-input form-input--narrow" type="number" placeholder="Poll interval (s)" value={(config['pollIntervalSeconds'] as number) ?? ''} onChange={e => onChange({ ...config, pollIntervalSeconds: Number(e.target.value) })} />
    </>
  );
}

function FileShareFields({ config, onChange }: { config: Record<string, unknown>; onChange: (c: Record<string, unknown>) => void }) {
  return (
    <>
      <input className="form-input" placeholder="Path" value={(config['path'] as string) ?? ''} onChange={e => onChange({ ...config, path: e.target.value })} />
      <input className="form-input" placeholder="File pattern" value={(config['filePattern'] as string) ?? ''} onChange={e => onChange({ ...config, filePattern: e.target.value })} />
    </>
  );
}

function AddConnectionForm({ onDone }: { onDone: () => void }) {
  const [displayName, setDisplayName] = useState('');
  const [type, setType] = useState<ConnectionType>('KAFKA');
  const [config, setConfig] = useState<Record<string, unknown>>({});
  const create = useCreateSourceConnection();

  function submit() {
    create.mutate({ displayName, type, config }, { onSuccess: onDone });
  }

  return (
    <div className="form-card">
      <h3>Add Connection</h3>
      <input className="form-input" placeholder="Display name" value={displayName} onChange={e => setDisplayName(e.target.value)} />
      <select className="form-input" value={type} onChange={e => { setType(e.target.value as ConnectionType); setConfig({}); }}>
        {CONNECTION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
      </select>
      {type === 'KAFKA' && <KafkaFields config={config} onChange={setConfig} />}
      {type === 'DB_POLL' && <DbPollFields config={config} onChange={setConfig} />}
      {type === 'FILE_SHARE' && <FileShareFields config={config} onChange={setConfig} />}
      <div className="button-row">
        <button type="button" className="btn btn-primary" onClick={submit} disabled={!displayName.trim() || create.isPending}>Save</button>
        <button type="button" className="btn btn-secondary" onClick={onDone}>Cancel</button>
      </div>
    </div>
  );
}

function AccessModal({ connection, onClose }: { connection: SourceConnection; onClose: () => void }) {
  const [tenantId, setTenantId] = useState('');
  const grant = useGrantConnectionAccess(connection.id);
  const revoke = useRevokeConnectionAccess(connection.id);

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal-header">
          <h3>Manage Access — {connection.displayName}</h3>
          <button type="button" className="btn btn-icon" onClick={onClose}>✕</button>
        </div>
        <div className="modal-body">
          <div className="button-row">
            <input
              className="form-input form-input--sm"
              placeholder="Tenant ID"
              value={tenantId}
              onChange={e => setTenantId(e.target.value)}
            />
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => grant.mutate({ tenantId }, { onSuccess: () => setTenantId('') })}
              disabled={!tenantId.trim() || grant.isPending}
            >
              Grant Access
            </button>
          </div>
          <p className="form-hint">To revoke access, enter the tenant ID and use the revoke button.</p>
          <div className="button-row">
            <input
              className="form-input form-input--sm"
              placeholder="Tenant ID to revoke"
              value={tenantId}
              onChange={e => setTenantId(e.target.value)}
            />
            <button
              type="button"
              className="btn btn-danger"
              onClick={() => revoke.mutate({ tenantId }, { onSuccess: () => setTenantId('') })}
              disabled={!tenantId.trim() || revoke.isPending}
            >
              Revoke Access
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function SourceConnectionsAdminView() {
  const { data: connections = [], isLoading, isError } = useSourceConnections();
  const [showAddForm, setShowAddForm] = useState(false);
  const [managingConnection, setManagingConnection] = useState<SourceConnection | null>(null);

  return (
    <div className="view-container">
      <div className="view-header">
        <h2 className="view-title">Source Connections</h2>
        <button type="button" className="btn btn-primary" onClick={() => setShowAddForm(true)}>
          + Add Connection
        </button>
      </div>

      {showAddForm && <AddConnectionForm onDone={() => setShowAddForm(false)} />}

      {isLoading && <p className="status-text">Loading…</p>}
      {isError && <p className="status-text status-text--error">Failed to load connections.</p>}

      {connections.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Display Name</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {connections.map(conn => (
              <tr key={conn.id}>
                <td><span className="badge">{conn.type}</span></td>
                <td>{conn.displayName}</td>
                <td>{new Date(conn.createdAt).toLocaleDateString()}</td>
                <td>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => setManagingConnection(conn)}
                  >
                    Manage Access
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {managingConnection && (
        <AccessModal connection={managingConnection} onClose={() => setManagingConnection(null)} />
      )}
    </div>
  );
}
