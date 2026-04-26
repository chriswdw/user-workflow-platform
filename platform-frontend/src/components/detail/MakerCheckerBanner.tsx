interface Props {
  pendingCheckerTransition: string;
}

export function MakerCheckerBanner({ pendingCheckerTransition }: Props) {
  return (
    <div className="maker-checker-banner" role="status" aria-live="polite">
      <strong>Awaiting second approver</strong> — this item requires checker approval for:{' '}
      <em>{pendingCheckerTransition}</em>
    </div>
  );
}
