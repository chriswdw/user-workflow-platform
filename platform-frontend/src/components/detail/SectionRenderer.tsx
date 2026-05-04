import { useState } from 'react';
import type { Section } from '../../types/DetailViewConfig';
import type { WorkItem } from '../../types/WorkItem';
import { resolve } from '../../utils/fieldPathResolver';
import { formatValue } from '../../utils/formatValue';
import { maskIfNeeded } from '../../utils/fieldMasking';

interface SectionRendererProps {
  readonly section: Section;
  readonly item: WorkItem;
  readonly userRole: string;
}

interface FieldValueProps {
  readonly formatter: string;
  readonly masked: unknown;
  readonly isEditable: boolean;
  readonly label: string;
}

function FieldValue({ formatter, masked, isEditable, label }: FieldValueProps) {
  if (formatter === 'BADGE' && masked != null) {
    return (
      <span className={`badge badge--${formatValue(masked).toLowerCase().replaceAll('_', '-')}`}>
        {formatValue(masked, formatter)}
      </span>
    );
  }
  if (isEditable) {
    return (
      <input
        className="field-inline-edit"
        defaultValue={masked != null ? formatValue(masked) : ''}
        aria-label={label}
      />
    );
  }
  return <>{formatValue(masked, formatter)}</>;
}

export function SectionRenderer({ section, item, userRole }: SectionRendererProps) {
  const [collapsed, setCollapsed] = useState(false);

  const visibleFields = section.fields.filter(
    f => !f.visibleRoles || f.visibleRoles.includes(userRole)
  );

  return (
    <section className={`section section--${section.layout.toLowerCase()}`}>
      <h2 className={section.collapsible ? 'section-heading section-heading--collapsible' : 'section-heading'}>
        {section.collapsible ? (
          <button
            type="button"
            className="section-collapse-toggle"
            aria-expanded={!collapsed}
            onClick={() => setCollapsed(c => !c)}
          >
            <span className="collapse-indicator" aria-hidden="true">{collapsed ? '▶' : '▼'}</span>
            {section.title}
          </button>
        ) : section.title}
      </h2>

      {!collapsed && (
        <dl>
          {visibleFields.map(f => {
            const topLevel = item[f.field as keyof WorkItem];
            const raw = topLevel !== undefined
              ? topLevel
              : resolve(item.fields as Record<string, unknown>, f.field);
            const masked = maskIfNeeded(raw, f.visibleRoles, userRole);
            const isEditable = !!f.editable &&
              (!f.editableInStates || f.editableInStates.includes(item.status));

            return (
              <div key={f.field} className="field-row">
                <dt>{f.label}</dt>
                <dd>
                  <FieldValue
                    formatter={f.formatter}
                    masked={masked}
                    isEditable={isEditable}
                    label={f.label}
                  />
                </dd>
              </div>
            );
          })}
        </dl>
      )}
    </section>
  );
}
