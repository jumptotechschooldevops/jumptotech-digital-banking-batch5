import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransactions } from "@/hooks/useTransactions";
import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";
import { Alert } from "@/components/ui/Alert";
import { Badge, statusTone } from "@/components/ui/Badge";
import { Table, TableHead, TableBody, Th, Td } from "@/components/ui/Table";
import { Pagination } from "@/components/ui/Pagination";
import { formatCents } from "@/utils/money";
import { formatDateTime } from "@/utils/date";
import type { EntryType } from "@/types/api";

export function TransactionsPage() {
  const [searchParams] = useSearchParams();
  const { data: accounts, isLoading: accountsLoading } = useAccounts();

  const [accountId, setAccountId] = useState<string>(searchParams.get("accountId") ?? "");
  const [type, setType] = useState<EntryType | "">("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (!accountId && accounts && accounts.length > 0) {
      setAccountId(accounts[0].id);
    }
  }, [accounts, accountId]);

  const { data, isLoading, isError, isFetching } = useTransactions(accountId || undefined, {
    page,
    size: 10,
    type: type || undefined,
  });

  if (accountsLoading) {
    return <LoadingSpinner label="Loading your accounts…" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Transaction history</h1>
        <p className="text-slate-500">Every transaction shown here is read directly from the backend ledger.</p>
      </div>

      <Card>
        <CardHeader title="Filters" />
        <div className="grid gap-4 sm:grid-cols-2">
          <Select
            label="Account"
            value={accountId}
            onChange={(e) => {
              setAccountId(e.target.value);
              setPage(0);
            }}
          >
            {accounts?.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountType} {account.maskedAccountNumber}
              </option>
            ))}
          </Select>
          <Select
            label="Type"
            value={type}
            onChange={(e) => {
              setType(e.target.value as EntryType | "");
              setPage(0);
            }}
          >
            <option value="">All</option>
            <option value="DEBIT">Sent (debit)</option>
            <option value="CREDIT">Received (credit)</option>
          </Select>
        </div>
      </Card>

      <Card>
        {isLoading ? (
          <LoadingSpinner label="Loading transactions…" />
        ) : isError || !data ? (
          <Alert variant="error">We couldn&apos;t load transactions right now. Please try again shortly.</Alert>
        ) : data.content.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">No transactions match these filters.</p>
        ) : (
          <>
            <Table>
              <TableHead>
                <Th>Date</Th>
                <Th>Type</Th>
                <Th>Amount</Th>
                <Th>Balance after</Th>
                <Th>Status</Th>
                <Th>Counterparty</Th>
                <Th>Description</Th>
              </TableHead>
              <TableBody>
                {data.content.map((txn) => (
                  <tr key={txn.id} className={isFetching ? "opacity-60" : undefined}>
                    <Td>{formatDateTime(txn.occurredAt)}</Td>
                    <Td>{txn.type === "DEBIT" ? "Sent" : "Received"}</Td>
                    <Td className={txn.type === "DEBIT" ? "text-red-600" : "text-teal-700"}>
                      {txn.type === "DEBIT" ? "-" : "+"}
                      {formatCents(txn.amountCents)}
                    </Td>
                    <Td>{formatCents(txn.balanceAfterCents)}</Td>
                    <Td>
                      <Badge tone={statusTone(txn.status)}>{txn.status}</Badge>
                    </Td>
                    <Td>{txn.counterpartyMaskedAccountNumber}</Td>
                    <Td>{txn.description ?? "—"}</Td>
                  </tr>
                ))}
              </TableBody>
            </Table>
            <Pagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
