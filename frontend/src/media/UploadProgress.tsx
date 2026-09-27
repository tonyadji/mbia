/**
 * The progress of a photo being sent (family-tree-ux.md §13): a bar and, once the share is known,
 * its percentage. `label` names the bar; `text` is the translated percentage.
 */
export function UploadProgress({
  percent,
  label,
  text,
}: {
  percent: number | null;
  label: string;
  text: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
        className="h-2 overflow-hidden rounded-full bg-border"
      >
        <div
          className="h-full rounded-full bg-primary transition-[width]"
          style={{ width: `${String(percent ?? 0)}%` }}
        />
      </div>
      {percent !== null && (
        <p className="text-caption text-text-muted" aria-live="polite">
          {text}
        </p>
      )}
    </div>
  );
}
