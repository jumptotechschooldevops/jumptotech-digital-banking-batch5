import { z } from "zod";

export const transferSchema = z
  .object({
    sourceAccountId: z.string().min(1, "Choose a source account"),
    destinationAccountId: z.string().min(1, "Choose a destination account"),
    amount: z
      .string()
      .min(1, "Enter an amount")
      .regex(/^\d+(\.\d{1,2})?$/, "Enter a valid dollar amount, e.g. 125.00")
      .refine((value) => Number.parseFloat(value) > 0, "Amount must be greater than zero"),
    description: z.string().max(500).optional(),
  })
  .refine((data) => data.sourceAccountId !== data.destinationAccountId, {
    message: "Source and destination accounts must be different",
    path: ["destinationAccountId"],
  });

export type TransferFormValues = z.infer<typeof transferSchema>;
