import type { ReactNode } from "react";

type AlertVariant = "error" | "success" | "info";

const variantClasses: Record<AlertVariant, string> = {
  error: "border-red-200 bg-red-50 text-red-800",
  success: "border-teal-200 bg-teal-50 text-teal-800",
  info: "border-navy-200 bg-navy-50 text-navy-800",
};

export function Alert({ variant = "info", children }: { variant?: AlertVariant; children: ReactNode }) {
  return (
    <div role={variant === "error" ? "alert" : "status"} className={`rounded-lg border px-4 py-3 text-sm ${variantClasses[variant]}`}>
      {children}
    </div>
  );
}
