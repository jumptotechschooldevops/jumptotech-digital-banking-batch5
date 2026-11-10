export function LoadingSpinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-10 text-slate-500" role="status">
      <span className="h-6 w-6 animate-spin rounded-full border-2 border-slate-300 border-t-navy-700" aria-hidden="true" />
      <span className="text-sm">{label}</span>
    </div>
  );
}
