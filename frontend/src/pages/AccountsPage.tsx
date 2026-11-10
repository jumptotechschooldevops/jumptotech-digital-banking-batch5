import { Link } from "react-router-dom";
import { useAccounts } from "@/hooks/useAccounts";
import { Card } from "@/components/ui/Card";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";
import { Alert } from "@/components/ui/Alert";
import { Badge, statusTone } from "@/components/ui/Badge";
import { formatCents } from "@/utils/money";

export function AccountsPage() {
  const { data: accounts, isLoading, isError } = useAccounts();

  if (isLoading) {
    return <LoadingSpinner label="Loading your accounts…" />;
  }

  if (isError || !accounts) {
    return <Alert variant="error">We couldn&apos;t load your accounts right now. Please try again shortly.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Accounts</h1>
        <p className="text-slate-500">All financial data below comes directly from your account records.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {accounts.map((account) => (
          <Card key={account.id}>
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm font-medium text-slate-500">
                  {account.accountType === "CHECKING" ? "Checking account" : "Savings account"}
                </p>
                <p className="mt-1 text-xs text-slate-400">{account.maskedAccountNumber}</p>
              </div>
              <Badge tone={statusTone(account.status)}>{account.status}</Badge>
            </div>
            <p className="mt-6 text-3xl font-bold text-navy-900">{formatCents(account.balanceCents, account.currency)}</p>
            <Link
              to={`/transactions?accountId=${account.id}`}
              className="mt-4 inline-block text-sm font-medium text-teal-700 hover:underline"
            >
              View transactions
            </Link>
          </Card>
        ))}
      </div>
    </div>
  );
}
