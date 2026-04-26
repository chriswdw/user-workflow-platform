export function resolve(obj: Record<string, unknown>, path: string): unknown {
  return path.split('.').reduce<unknown>((acc, key) =>
    acc != null && typeof acc === 'object'
      ? (acc as Record<string, unknown>)[key]
      : undefined,
    obj
  );
}
