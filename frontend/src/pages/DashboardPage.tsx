import { useQueries } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { useAccounts } from "@/hooks/useAccounts";
import { fetchTransactions } from "@/api/accounts";
import { Card, CardHeader } from "@/components/ui/Card";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";
import { Alert } from "@/components/ui/Alert";
import { Badge, statusTone } from "@/components/ui/Badge";
import { Table, TableHead, TableBody, Th, Td } from "@/components/ui/Table";
import { formatCents } from "@/utils/money";
import { formatDateTime } from "@/utils/date";
import type { TransactionEntry } from "@/types/api";

export function DashboardPage() {
  const { user } = useAuth();
  const { data: accounts, isLoading: accountsLoading, isError: accountsError } = useAccounts();

  const transactionQueries = useQueries({
    queries: (accounts ?? []).map((account) => ({
      queryKey: ["transactions", account.id, { page: 0, size: 5 }],
      queryFn: () => fetchTransactions(account.id, { page: 0, size: 5 }),
      enabled: Boolean(accounts),
    })),
  });

  if (accountsLoading) {
    return <LoadingSpinner label="Loading your dashboard…" />;
  }

  if (accountsError || !accounts) {
    return <Alert variant="error">We couldn&apos;t load your accounts right now. Please try again shortly.</Alert>;
  }

  const totalBalanceCents = accounts.reduce((sum, account) => sum + account.balanceCents, 0);
  const checking = accounts.find((a) => a.accountType === "CHECKING");
  const savings = accounts.find((a) => a.accountType === "SAVINGS");

  const recentTransactions: TransactionEntry[] = transactionQueries
    .flatMap((query) => query.data?.content ?? [])
    .sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
    .slice(0, 5);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Welcome back, {user?.firstName}</h1>
        <p className="text-slate-500">Here&apos;s a snapshot of your accounts.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-sm font-medium text-slate-500">Total balance</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">{formatCents(totalBalanceCents)}</p>
        </Card>
        <Card>
          <p className="text-sm font-medium text-slate-500">Checking</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">
            {checking ? formatCents(checking.balanceCents) : "—"}
          </p>
          {checking && <p className="mt-1 text-xs text-slate-400">{checking.maskedAccountNumber}</p>}
        </Card>
        <Card>
          <p className="text-sm font-medium text-slate-500">Savings</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">
            {savings ? formatCents(savings.balanceCents) : "—"}
          </p>
          {savings && <p className="mt-1 text-xs text-slate-400">{savings.maskedAccountNumber}</p>}
        </Card>
      </div>

      <Card>
        <CardHeader
          title="Recent transactions"
          action={
            <Link to="/transactions" className="text-sm font-medium text-teal-700 hover:underline">
              View all
            </Link>
          }
        />
        {recentTransactions.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">No transactions yet.</p>
        ) : (
          <Table>
            <TableHead>
              <Th>Date</Th>
              <Th>Type</Th>
              <Th>Amount</Th>
              <Th>Status</Th>
            </TableHead>
            <TableBody>
              {recentTransactions.map((txn) => (
                <tr key={txn.id}>
                  <Td>{formatDateTime(txn.occurredAt)}</Td>
                  <Td>{txn.type === "DEBIT" ? "Sent" : "Received"}</Td>
                  <Td className={txn.type === "DEBIT" ? "text-red-600" : "text-teal-700"}>
                    {txn.type === "DEBIT" ? "-" : "+"}
                    {formatCents(txn.amountCents)}
                  </Td>
                  <Td>
                    <Badge tone={statusTone(txn.status)}>{txn.status}</Badge>
                  </Td>
                </tr>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
