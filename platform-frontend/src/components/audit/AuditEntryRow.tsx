import { useState } from 'react';
import type { AuditEntry } from '../../types/AuditEntry';

interface AuditEntryRowProps {
  readonly entry: AuditEntry;
}

const eventTypeColor: Record<string, string> = {
  SLA_BREACH: '#fff3cd',
  SLA_WARNING: '#fff8e1',
  TRANSITION_FAILED: '#fde8e8',
  INGESTION: '#e8f0fe',
};

function formatFieldValue(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export function AuditEntryRow({ entry }: AuditEntryRowProps) {
  const [expanded, setExpanded] = useState(false);
  const rowStyle = { backgroundColor: eventTypeColor[entry.eventType] ?? undefined };

  return (
    <>
      <tr
        style={{ ...rowStyle, cursor: entry.changedFields.length > 0 ? 'pointer' : undefined }}
        onClick={() => entry.changedFields.length > 0 && setExpanded(e => !e)}
      >
        <td>{new Date(entry.timestamp).toLocaleString()}</td>
        <td>{entry.eventType}</td>
        <td>{entry.actorUserId}</td>
        <td>{entry.actorRole}</td>
        <td>{entry.previousState ?? '—'}</td>
        <td>{entry.newState ?? '—'}</td>
        <td>{entry.transitionName ?? '—'}</td>
        <td>{entry.changedFields.length > 0 ? (expanded ? '▲' : '▶') : ''}</td>
      </tr>
      {expanded && entry.changedFields.length > 0 && (
        <tr style={rowStyle}>
          <td colSpan={8} style={{ paddingLeft: 32, paddingBottom: 8 }}>
            <table className="audit-changed-fields">
              <thead>
                <tr>
                  <th>Field</th>
                  <th>Previous</th>
                  <th>New</th>
                </tr>
              </thead>
              <tbody>
                {entry.changedFields.map(cf => (
                  <tr key={cf.fieldPath}>
                    <td>{cf.fieldPath}</td>
                    <td>{formatFieldValue(cf.previousValue)}</td>
                    <td>{formatFieldValue(cf.newValue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </td>
        </tr>
      )}
    </>
  );
}
