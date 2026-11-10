import { useAdminSummary } from "@/hooks/useAdminSummary";
import { Card, CardHeader } from "@/components/ui/Card";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";
import { Alert } from "@/components/ui/Alert";
import { Badge, statusTone } from "@/components/ui/Badge";
import { Table, TableHead, TableBody, Th, Td } from "@/components/ui/Table";
import { formatCents } from "@/utils/money";
import { formatDateTime } from "@/utils/date";

export function AdminPage() {
  const { data, isLoading, isError } = useAdminSummary();

  if (isLoading) {
    return <LoadingSpinner label="Loading admin summary…" />;
  }

  if (isError || !data) {
    return <Alert variant="error">We couldn&apos;t load the admin summary right now. Please try again shortly.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Admin dashboard</h1>
        <p className="text-slate-500">Aggregate, read-only view across all simulated customers.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <p className="text-sm font-medium text-slate-500">Total customers</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">{data.totalCustomers}</p>
        </Card>
        <Card>
          <p className="text-sm font-medium text-slate-500">Total accounts</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">{data.totalAccounts}</p>
        </Card>
        <Card>
          <p className="text-sm font-medium text-slate-500">Total simulated balance</p>
          <p className="mt-2 text-2xl font-bold text-navy-900">{formatCents(data.totalBalanceCents)}</p>
        </Card>
      </div>

      <Card>
        <CardHeader title="Recent transfers" subtitle="Across every customer" />
        {data.recentTransfers.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">No transfers yet.</p>
        ) : (
          <Table>
            <TableHead>
              <Th>Transfer ID</Th>
              <Th>Amount</Th>
              <Th>Status</Th>
              <Th>Completed</Th>
            </TableHead>
            <TableBody>
              {data.recentTransfers.map((transfer) => (
                <tr key={transfer.transferId}>
                  <Td className="font-mono text-xs">{transfer.transferId.slice(0, 8)}</Td>
                  <Td>{formatCents(transfer.amountCents)}</Td>
                  <Td>
                    <Badge tone={statusTone(transfer.status)}>{transfer.status}</Badge>
                  </Td>
                  <Td>{transfer.completedAt ? formatDateTime(transfer.completedAt) : "—"}</Td>
                </tr>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
