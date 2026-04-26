import type { BlotterConfig } from '../types/BlotterConfig';

export const BLOTTER_CONFIGS: Record<string, BlotterConfig> = {
  SETTLEMENT_EXCEPTION: {
    id: 'blotter-settlement-exception',
    tenantId: 'tenant-1',
    workflowType: 'SETTLEMENT_EXCEPTION',
    columns: [
      { field: 'id',                          header: 'ID',           visible: true, width: 120 },
      { field: 'status',                       header: 'Status',       visible: true, width: 140, formatter: 'BADGE' },
      { field: 'priorityLevel',               header: 'Priority',     visible: true, width: 110, formatter: 'BADGE' },
      { field: 'trade.ref',                    header: 'Trade Ref',    visible: true, width: 160 },
      { field: 'trade.valueDate',              header: 'Value Date',   visible: true, width: 120, formatter: 'DATE' },
      { field: 'trade.notionalAmount.amount',  header: 'Notional',     visible: true, width: 140, formatter: 'CURRENCY', maskingRoles: ['ANALYST', 'SUPERVISOR'] },
      { field: 'trade.notionalAmount.currency',header: 'Ccy',          visible: true, width: 70 },
      { field: 'counterparty.name',            header: 'Counterparty', visible: true, width: 200 },
      { field: 'source',                       header: 'Source',       visible: true, width: 110 },
      { field: 'assignedGroup',                header: 'Group',        visible: true, width: 130 },
      { field: 'updatedAt',                    header: 'Last Updated', visible: true, width: 170, formatter: 'DATETIME' },
    ],
    defaultSort: { field: 'priorityLevel', direction: 'DESC' },
    active: true,
    version: 1,
  },
};

export const WORKFLOW_TYPES = Object.keys(BLOTTER_CONFIGS);
