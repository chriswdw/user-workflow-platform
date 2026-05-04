import { useMemo } from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ValueGetterParams } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-alpine.css';
import type { BlotterConfig } from '../../types/BlotterConfig';
import type { WorkItem } from '../../types/WorkItem';
import { resolve } from '../../utils/fieldPathResolver';
import { maskIfNeeded } from '../../utils/fieldMasking';

interface BlotterProps {
  readonly config: BlotterConfig;
  readonly items: readonly WorkItem[];
  readonly userRole: string;
  readonly onSelectItem: (id: string) => void;
}

export function Blotter({ config, items, userRole, onSelectItem }: BlotterProps) {
  const colDefs = useMemo<ColDef<WorkItem>[]>(() =>
    config.columns
      .filter(col => col.visible !== false)
      .map(col => ({
        headerName: col.header,
        field: col.field as keyof WorkItem,
        width: col.width,
        sortable: col.sortable ?? true,
        filter: col.filterable ?? false,
        valueGetter: (params: ValueGetterParams<WorkItem>) => {
          if (!params.data) return undefined;
          const item = params.data;
          // Resolve from top-level fields first, then from the nested fields map
          const topLevel = item[col.field as keyof WorkItem];
          const raw = topLevel !== undefined
            ? topLevel
            : resolve(item.fields as Record<string, unknown>, col.field);

          if (raw === undefined) {
            console.warn(`blotter: field path not found: ${col.field}`);
            return '—';
          }
          return maskIfNeeded(raw, col.maskingRoles, userRole);
        },
      })),
    [config.columns, userRole]
  );

  return (
    <div className="ag-theme-alpine" style={{ height: 600, width: '100%' }}>
      <AgGridReact<WorkItem>
        rowData={[...items]}
        columnDefs={colDefs}
        onRowClicked={e => e.data && onSelectItem(e.data.id)}
      />
    </div>
  );
}
