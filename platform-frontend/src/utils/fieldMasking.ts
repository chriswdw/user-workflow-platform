export function maskIfNeeded(
  value: unknown,
  maskingRoles: string[] | undefined,
  userRole: string
): unknown {
  if (!maskingRoles || maskingRoles.length === 0) return value;
  return maskingRoles.includes(userRole) ? value : '***';
}
