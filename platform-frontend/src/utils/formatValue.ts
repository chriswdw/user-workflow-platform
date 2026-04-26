export function formatValue(raw: unknown, formatter?: string): string {
  if (raw == null) return '—';
  switch (formatter) {
    case 'CURRENCY': {
      const num = typeof raw === 'number' ? raw : parseFloat(String(raw));
      if (isNaN(num)) return String(raw);
      return num.toLocaleString('en-US', { style: 'currency', currency: 'USD' });
    }
    case 'DATE': {
      const d = new Date(String(raw));
      return isNaN(d.getTime()) ? String(raw) : d.toLocaleDateString('en-US');
    }
    case 'DATETIME': {
      const d = new Date(String(raw));
      return isNaN(d.getTime()) ? String(raw) : d.toLocaleString('en-US');
    }
    case 'PERCENTAGE':
      return `${raw}%`;
    case 'BADGE':
    case 'TEXT':
    default:
      return String(raw);
  }
}
