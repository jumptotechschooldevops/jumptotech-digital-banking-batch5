/** Backend amounts are always integer cents; this is the only place that turns them into display text. */
export function formatCents(cents: number, currency = "USD"): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

export function dollarsStringToCents(value: string): number {
  const normalized = value.replace(/[^0-9.]/g, "");
  const dollars = Number.parseFloat(normalized || "0");
  return Math.round(dollars * 100);
}
