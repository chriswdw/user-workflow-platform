import type { Action } from '../types/DetailViewConfig';

export function isActionVisible(action: Action, currentState: string, userRole?: string): boolean {
  if (!action.visibleInStates.includes(currentState)) return false;
  if (action.visibleRoles && action.visibleRoles.length > 0) {
    if (!userRole || !action.visibleRoles.includes(userRole)) return false;
  }
  return true;
}
