import { useState } from 'react';
import type { Section } from '../../types/DetailViewConfig';
import type { WorkItem } from '../../types/WorkItem';
import { resolve } from '../../utils/fieldPathResolver';
import { formatValue } from '../../utils/formatValue';
import { maskIfNeeded } from '../../utils/fieldMasking';

interface Props {
  section: Section;
  item: WorkItem;
  userRole: string;
}

export function SectionRenderer({ section, item, userRole }: Props) {
  const [collapsed, setCollapsed] = useState(false);

  const visibleFields = section.fields.filter(
    f => !f.visibleRoles || f.visibleRoles.includes(userRole)
  );

  return (
    <section className={`section section--${section.layout.toLowerCase()}`}>
      <h2
        className={section.collapsible ? 'section-heading section-heading--collapsible' : 'section-heading'}
        onClick={section.collapsible ? () => setCollapsed(c => !c) : undefined}
        aria-expanded={section.collapsible ? !collapsed : undefined}
      >
        {section.collapsible && <span className="collapse-indicator">{collapsed ? '▶' : '▼'}</span>}
        {section.title}
      </h2>

      {!collapsed && (
        <dl>
          {visibleFields.map(f => {
            const topLevel = item[f.field as keyof WorkItem];
            const raw = topLevel !== undefined
              ? topLevel
              : resolve(item.fields as Record<string, unknown>, f.field);

            const masked = maskIfNeeded(raw, f.visibleRoles, userRole);
            const display = formatValue(masked, f.formatter);
            const isEditable = f.editable &&
              (!f.editableInStates || f.editableInStates.includes(item.status));

            return (
              <div key={f.field} className="field-row">
                <dt>{f.label}</dt>
                <dd>
                  {f.formatter === 'BADGE' && masked != null ? (
                    <span className={`badge badge--${String(masked).toLowerCase().replace(/_/g, '-')}`}>
                      {display}
                    </span>
                  ) : isEditable ? (
                    <input
                      className="field-inline-edit"
                      defaultValue={masked != null ? String(masked) : ''}
                      aria-label={f.label}
                    />
                  ) : (
                    display
                  )}
                </dd>
              </div>
            );
          })}
        </dl>
      )}
    </section>
  );
}
