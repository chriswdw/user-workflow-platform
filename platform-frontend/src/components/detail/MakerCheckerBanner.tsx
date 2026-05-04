interface MakerCheckerBannerProps {
  readonly pendingCheckerTransition: string;
}

export function MakerCheckerBanner({ pendingCheckerTransition }: MakerCheckerBannerProps) {
  return (
    <div className="maker-checker-banner" role="status" aria-live="polite">
      <strong>Awaiting second approver</strong> — this item requires checker approval for:{' '}
      <em>{pendingCheckerTransition}</em>
    </div>
  );
}
