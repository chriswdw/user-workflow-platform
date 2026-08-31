function toDisplayString(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

export function formatValue(raw: unknown, formatter?: string): string {
  if (raw == null) return '—';
  switch (formatter) {
    case 'CURRENCY': {
      const num = typeof raw === 'number' ? raw : Number.parseFloat(toDisplayString(raw));
      if (Number.isNaN(num)) return toDisplayString(raw);
      return num.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
    }
    case 'DATE': {
      const d = new Date(toDisplayString(raw));
      return Number.isNaN(d.getTime()) ? toDisplayString(raw) : d.toLocaleDateString('en-US');
    }
    case 'DATETIME': {
      const d = new Date(toDisplayString(raw));
      return Number.isNaN(d.getTime()) ? toDisplayString(raw) : d.toLocaleString('en-US');
    }
    case 'PERCENTAGE':
      return `${toDisplayString(raw)}%`;
    case 'BADGE':
    case 'TEXT':
    default:
      return toDisplayString(raw);
  }
}
