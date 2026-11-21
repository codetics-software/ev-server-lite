import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  date,
  day,
  display,
  duration,
  fromInputDateTime,
  hours,
  initials,
  kw,
  kwh,
  money,
  moneyMinor,
  number,
  percent,
  relative,
  time,
  toInputDate,
  toInputDateTime,
} from "@/lib/format";
import { setActiveLocale } from "@/lib/i18n";

const spaces = (value: string) => value.replace(/[  ]/g, " ");

describe("format", () => {
  beforeEach(() => setActiveLocale("fr"));

  afterEach(() => {
    vi.useRealTimers();
    setActiveLocale("fr");
  });

  it("displays empty values with a dash", () => {
    expect(display(null)).toBe("—");
    expect(display(undefined)).toBe("—");
    expect(display("")).toBe("—");
  });

  it("displays booleans in the active language", () => {
    expect(display(true)).toBe("Oui");
    expect(display(false)).toBe("Non");
    setActiveLocale("en");
    expect(display(true)).toBe("Yes");
  });

  it("serialises objects and keeps unknown strings", () => {
    expect(display({ a: 1 })).toBe('{"a":1}');
    expect(display("Charging-unknown-value")).toBe("Charging-unknown-value");
    expect(display(42)).toBe("42");
  });

  it("formats numbers and units only for finite numbers", () => {
    expect(spaces(number(1234.5, 1))).toBe("1 234,5");
    expect(number("abc")).toBe("—");
    expect(number("  ")).toBe("—");
    expect(number(Number.NaN)).toBe("—");
    expect(spaces(kwh(12.345))).toBe("12,35 kWh");
    expect(kwh(null)).toBe("—");
    expect(spaces(kw("22"))).toBe("22 kW");
    expect(kw(undefined)).toBe("—");
    expect(spaces(percent(80))).toBe("80 %");
    expect(percent("x")).toBe("—");
    expect(spaces(hours(1.25))).toBe("1,3 h");
    expect(hours(null)).toBe("—");
  });

  it("formats money in major and minor units", () => {
    expect(spaces(money(12.5))).toBe("12,50 €");
    expect(spaces(money(3, "USD"))).toContain("$");
    expect(spaces(money(3, ""))).toBe("3,00 €");
    expect(money("n/a")).toBe("—");
    expect(spaces(moneyMinor(1999))).toBe("19,99 €");
    expect(moneyMinor(undefined)).toBe("—");
  });

  it("formats durations", () => {
    expect(duration(45)).toBe("45 s");
    expect(duration(125)).toBe("2 min");
    expect(duration(3725)).toBe("1 h 02");
    expect(duration(-10)).toBe("0 s");
    expect(duration("bad")).toBe("—");
  });

  it("formats dates and rejects invalid ones", () => {
    expect(date(null)).toBe("—");
    expect(date("not a date")).toBe("not a date");
    expect(date("2026-03-04T10:20:00Z")).toMatch(/2026/);
    expect(day("2026-03-04T10:20:00Z")).toMatch(/2026/);
    expect(day("nope")).toBe("—");
    expect(time("2026-03-04T10:20:00Z")).toMatch(/\d{2}:\d{2}/);
    expect(time(undefined)).toBe("—");
  });

  it("formats relative times", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-03-04T12:00:00Z"));
    expect(relative("2026-03-04T12:00:10Z")).toBe("à l’instant");
    expect(relative("2026-03-04T11:55:00Z")).toBe("il y a 5 minutes");
    expect(relative("2026-03-04T09:00:00Z")).toBe("il y a 3 heures");
    expect(relative("2026-03-03T12:00:00Z")).toBe("hier");
    expect(relative("garbage")).toBe("—");
  });

  it("round-trips datetime-local inputs", () => {
    const iso = "2026-03-04T10:20:00.000Z";
    const local = toInputDateTime(iso);
    expect(local).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
    expect(fromInputDateTime(local)).toBe(iso);
    expect(toInputDate(iso)).toHaveLength(10);
    expect(toInputDateTime("bad")).toBe("");
    expect(fromInputDateTime("")).toBeNull();
    expect(fromInputDateTime("bad")).toBeNull();
  });

  it("builds initials", () => {
    expect(initials("kevin vavelin codetics")).toBe("KV");
    expect(initials("   ")).toBe("C");
  });
});
