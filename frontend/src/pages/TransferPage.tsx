import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAccounts } from "@/hooks/useAccounts";
import { useTransfer, newIdempotencyKey } from "@/hooks/useTransfer";
import { transferSchema, type TransferFormValues } from "@/schemas/transfer";
import { Card } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Alert } from "@/components/ui/Alert";
import { LoadingSpinner } from "@/components/ui/LoadingSpinner";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { formatCents, dollarsStringToCents } from "@/utils/money";
import { ApiError } from "@/lib/apiClient";
import type { TransferConfirmation } from "@/types/api";

export function TransferPage() {
  const { data: accounts, isLoading } = useAccounts();
  const transfer = useTransfer();
  const [pendingValues, setPendingValues] = useState<TransferFormValues | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey());
  const [receipt, setReceipt] = useState<TransferConfirmation | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<TransferFormValues>({ resolver: zodResolver(transferSchema) });

  const sourceId = watch("sourceAccountId");
  const destinationId = watch("destinationAccountId");

  const sourceAccount = useMemo(() => accounts?.find((a) => a.id === sourceId), [accounts, sourceId]);
  const destinationAccount = useMemo(() => accounts?.find((a) => a.id === destinationId), [accounts, destinationId]);

  if (isLoading) {
    return <LoadingSpinner label="Loading your accounts…" />;
  }

  function openConfirmation(values: TransferFormValues) {
    setPendingValues(values);
  }

  async function confirmTransfer() {
    if (!pendingValues) return;
    try {
      const result = await transfer.mutateAsync({
        payload: {
          sourceAccountId: pendingValues.sourceAccountId,
          destinationAccountId: pendingValues.destinationAccountId,
          amountCents: dollarsStringToCents(pendingValues.amount),
          description: pendingValues.description || undefined,
        },
        idempotencyKey,
      });
      setReceipt(result);
      setPendingValues(null);
    } catch {
      setPendingValues(null);
    }
  }

  function startNewTransfer() {
    setReceipt(null);
    setIdempotencyKey(newIdempotencyKey());
    reset();
  }

  if (receipt) {
    return (
      <Card className="mx-auto max-w-lg">
        <div className="mb-4 flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-teal-100 text-teal-700" aria-hidden="true">
            ✓
          </span>
          <div>
            <h1 className="text-lg font-semibold text-navy-900">Transfer complete</h1>
            <p className="text-sm text-slate-500">Receipt #{receipt.transferId.slice(0, 8)}</p>
          </div>
        </div>
        <dl className="grid grid-cols-2 gap-y-3 text-sm">
          <dt className="text-slate-500">Amount</dt>
          <dd className="text-right font-semibold text-navy-900">{formatCents(receipt.amountCents)}</dd>
          <dt className="text-slate-500">From</dt>
          <dd className="text-right">{receipt.sourceAccount.maskedAccountNumber}</dd>
          <dt className="text-slate-500">To</dt>
          <dd className="text-right">{receipt.destinationAccount.maskedAccountNumber}</dd>
          <dt className="text-slate-500">New source balance</dt>
          <dd className="text-right">{formatCents(receipt.sourceAccount.balanceCents)}</dd>
          <dt className="text-slate-500">New destination balance</dt>
          <dd className="text-right">{formatCents(receipt.destinationAccount.balanceCents)}</dd>
        </dl>
        <Button className="mt-6 w-full" onClick={startNewTransfer}>
          Make another transfer
        </Button>
      </Card>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Transfer money</h1>
        <p className="text-slate-500">Move simulated funds between your eligible accounts.</p>
      </div>

      <Card className="mx-auto w-full max-w-lg">
        {transfer.isError && (
          <div className="mb-4">
            <Alert variant="error">
              {transfer.error instanceof ApiError ? transfer.error.message : "The transfer could not be completed."}
            </Alert>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={handleSubmit(openConfirmation)} noValidate>
          <Select label="From account" error={errors.sourceAccountId?.message} {...register("sourceAccountId")}>
            <option value="">Select source account</option>
            {accounts?.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountType} {account.maskedAccountNumber} — {formatCents(account.balanceCents)}
              </option>
            ))}
          </Select>

          <Select
            label="To account"
            error={errors.destinationAccountId?.message}
            {...register("destinationAccountId")}
          >
            <option value="">Select destination account</option>
            {accounts?.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountType} {account.maskedAccountNumber} — {formatCents(account.balanceCents)}
              </option>
            ))}
          </Select>

          <Input
            label="Amount (USD)"
            inputMode="decimal"
            placeholder="0.00"
            error={errors.amount?.message}
            {...register("amount")}
          />

          <Input
            label="Description (optional)"
            placeholder="e.g. Move to savings"
            error={errors.description?.message}
            {...register("description")}
          />

          <Button type="submit" className="mt-2 w-full">
            Review transfer
          </Button>
        </form>
      </Card>

      <ConfirmDialog
        open={Boolean(pendingValues)}
        title="Confirm transfer"
        isConfirming={transfer.isPending}
        onConfirm={confirmTransfer}
        onCancel={() => setPendingValues(null)}
        confirmLabel="Confirm transfer"
      >
        {pendingValues && (
          <div className="flex flex-col gap-2">
            <p>
              You&apos;re about to transfer{" "}
              <span className="font-semibold text-navy-900">
                {formatCents(dollarsStringToCents(pendingValues.amount))}
              </span>
            </p>
            <p>
              From <span className="font-medium">{sourceAccount?.maskedAccountNumber}</span> to{" "}
              <span className="font-medium">{destinationAccount?.maskedAccountNumber}</span>.
            </p>
            <p className="text-xs text-slate-500">This action cannot be undone once confirmed.</p>
          </div>
        )}
      </ConfirmDialog>
    </div>
  );
}
