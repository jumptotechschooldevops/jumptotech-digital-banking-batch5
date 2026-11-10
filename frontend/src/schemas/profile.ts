import { z } from "zod";

export const profileSchema = z.object({
  phone: z
    .string()
    .trim()
    .optional()
    .refine((value) => !value || /^[0-9+()\-. ]{7,20}$/.test(value), "Enter a valid phone number"),
  addressLine1: z.string().max(200).optional(),
  addressLine2: z.string().max(200).optional(),
  city: z.string().max(100).optional(),
  state: z.string().max(100).optional(),
  postalCode: z.string().max(20).optional(),
});

export type ProfileFormValues = z.infer<typeof profileSchema>;
