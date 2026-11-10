import { apiFetch } from "@/lib/apiClient";
import type { TransferConfirmation } from "@/types/api";

export interface CreateTransferPayload {
  sourceAccountId: string;
  destinationAccountId: string;
  amountCents: number;
  description?: string;
}

export async function createTransfer(
  payload: CreateTransferPayload,
  idempotencyKey: string,
): Promise<TransferConfirmation> {
  return apiFetch<TransferConfirmation>("/transfers", {
    method: "POST",
    body: payload,
    idempotencyKey,
  });
}
