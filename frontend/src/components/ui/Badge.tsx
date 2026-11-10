type BadgeTone = "neutral" | "positive" | "negative" | "warning";

const toneClasses: Record<BadgeTone, string> = {
  neutral: "bg-slate-100 text-slate-700",
  positive: "bg-teal-100 text-teal-800",
  negative: "bg-red-100 text-red-700",
  warning: "bg-amber-100 text-amber-800",
};

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${toneClasses[tone]}`}>
      {children}
    </span>
  );
}

export function statusTone(status: string): BadgeTone {
  switch (status) {
    case "COMPLETED":
    case "ACTIVE":
      return "positive";
    case "FAILED":
    case "CLOSED":
      return "negative";
    case "PENDING":
    case "FROZEN":
      return "warning";
    default:
      return "neutral";
  }
}
