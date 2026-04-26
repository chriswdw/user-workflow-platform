import type { AuditEntry } from '../../types/AuditEntry';
import { AuditEntryRow } from './AuditEntryRow';

interface Props {
  entries: AuditEntry[];
}

export function AuditTrail({ entries }: Props) {
  if (entries.length === 0) {
    return <p className="audit-empty">No audit entries.</p>;
  }

  const sorted = [...entries].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  return (
    <table className="audit-table">
      <thead>
        <tr>
          <th>Timestamp</th>
          <th>Event</th>
          <th>Actor</th>
          <th>Role</th>
          <th>From State</th>
          <th>To State</th>
          <th>Transition</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {sorted.map(entry => (
          <AuditEntryRow key={entry.id} entry={entry} />
        ))}
      </tbody>
    </table>
  );
}
