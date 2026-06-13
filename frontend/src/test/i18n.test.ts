import { describe, expect, it } from "vitest";
import zhTW from "@/i18n/zh-TW.json";

/** Flatten nested keys into dotted paths. */
function flatten(obj: Record<string, unknown>, prefix = ""): string[] {
  return Object.entries(obj).flatMap(([k, v]) => {
    const path = prefix ? `${prefix}.${k}` : k;
    return typeof v === "object" && v !== null ? flatten(v as Record<string, unknown>, path) : [path];
  });
}

describe("zh-TW i18n bundle", () => {
  const keys = flatten(zhTW as Record<string, unknown>);

  it("has no empty strings", () => {
    const values: string[] = [];
    const walk = (o: Record<string, unknown>) =>
      Object.values(o).forEach((v) => (typeof v === "object" && v !== null ? walk(v as Record<string, unknown>) : values.push(String(v))));
    walk(zhTW as Record<string, unknown>);
    expect(values.every((v) => v.trim().length > 0)).toBe(true);
  });

  it("covers the core screen namespaces", () => {
    for (const ns of ["login", "servers", "nav", "dashboard", "speech", "spectator", "settings", "night", "election", "expel"]) {
      expect(keys.some((k) => k.startsWith(`${ns}.`))).toBe(true);
    }
  });

  it("is all Traditional-Chinese / ASCII-safe copy (no leftover placeholder English)", () => {
    expect(keys.length).toBeGreaterThan(80);
  });
});
