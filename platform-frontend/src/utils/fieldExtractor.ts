export function extractFieldPaths(obj: Record<string, unknown>, prefix = ''): string[] {
  const paths: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      paths.push(...extractFieldPaths(value as Record<string, unknown>, path));
    } else {
      paths.push(path);
    }
  }
  return paths;
}

export function inferType(value: unknown): 'DATE' | 'DECIMAL' | 'BOOLEAN' | 'STRING' {
  if (typeof value === 'boolean') return 'BOOLEAN';
  if (typeof value === 'number') return 'DECIMAL';
  if (typeof value === 'string') {
    if (/^\d{4}-\d{2}-\d{2}/.test(value)) return 'DATE';
    if (/^-?\d+(\.\d+)?$/.test(value)) return 'DECIMAL';
  }
  return 'STRING';
}

export function extractCsvHeaders(csv: string): string[] {
  const firstLine = csv.split('\n')[0] ?? '';
  if (!firstLine.trim()) return [];
  return firstLine.split(',').map(h => h.trim());
}

export function isSupportedFileType(filename: string): boolean {
  return /\.(json|csv)$/i.test(filename);
}
