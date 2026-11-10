import { describe, expect, it } from "vitest";
import { transferSchema } from "./transfer";

const base = {
  sourceAccountId: "11111111-1111-1111-1111-111111111111",
  destinationAccountId: "22222222-2222-2222-2222-222222222222",
  amount: "25.00",
  description: "",
};

describe("transferSchema", () => {
  it("accepts a valid transfer", () => {
    const result = transferSchema.safeParse(base);
    expect(result.success).toBe(true);
  });

  it("rejects a zero amount", () => {
    const result = transferSchema.safeParse({ ...base, amount: "0" });
    expect(result.success).toBe(false);
  });

  it("rejects a negative or malformed amount", () => {
    expect(transferSchema.safeParse({ ...base, amount: "-5" }).success).toBe(false);
    expect(transferSchema.safeParse({ ...base, amount: "abc" }).success).toBe(false);
    expect(transferSchema.safeParse({ ...base, amount: "12.345" }).success).toBe(false);
  });

  it("rejects source and destination being the same account", () => {
    const result = transferSchema.safeParse({ ...base, destinationAccountId: base.sourceAccountId });
    expect(result.success).toBe(false);
  });

  it("requires both accounts to be selected", () => {
    expect(transferSchema.safeParse({ ...base, sourceAccountId: "" }).success).toBe(false);
    expect(transferSchema.safeParse({ ...base, destinationAccountId: "" }).success).toBe(false);
  });
});
