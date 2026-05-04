import type { DetailViewConfig } from '../../types/DetailViewConfig';
import type { WorkItem } from '../../types/WorkItem';
import { isActionVisible } from '../../utils/actionVisibility';
import { SectionRenderer } from './SectionRenderer';
import { ActionButton } from './ActionButton';
import { MakerCheckerBanner } from './MakerCheckerBanner';

interface DetailViewProps {
  readonly config: DetailViewConfig;
  readonly item: WorkItem;
  readonly userRole: string;
  readonly onTransition: (transition: string, additionalFields?: Record<string, unknown>) => void;
  readonly transitionError?: string;
}

export function DetailView({ config, item, userRole, onTransition, transitionError }: DetailViewProps) {
  const visibleActions = config.actions.filter(a => isActionVisible(a, item.status, userRole));

  return (
    <div className="detail-view">
      {item.pendingCheckerId && item.pendingCheckerTransition && (
        <MakerCheckerBanner pendingCheckerTransition={item.pendingCheckerTransition} />
      )}

      {config.sections.map(section => (
        <SectionRenderer
          key={section.title}
          section={section}
          item={item}
          userRole={userRole}
        />
      ))}

      {visibleActions.length > 0 && (
        <div className="actions">
          {visibleActions.map(action => (
            <ActionButton
              key={action.transition}
              action={action}
              onTransition={onTransition}
              serverError={transitionError}
            />
          ))}
        </div>
      )}
    </div>
  );
}
