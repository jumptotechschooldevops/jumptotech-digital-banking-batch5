import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createTransfer, type CreateTransferPayload } from "@/api/transfers";

export function useTransfer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ payload, idempotencyKey }: { payload: CreateTransferPayload; idempotencyKey: string }) =>
      createTransfer(payload, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["accounts"] });
      void queryClient.invalidateQueries({ queryKey: ["transactions"] });
    },
  });
}

/** One fresh key per transfer attempt - regenerated whenever the form data changes, see TransferPage. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
