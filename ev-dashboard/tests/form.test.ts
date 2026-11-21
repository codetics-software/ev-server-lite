import { describe, expect, it } from "vitest";
import {
  asText,
  email,
  integerBetween,
  isInvalid,
  lengthBetween,
  matches,
  maxLength,
  numeric,
  optionalNumber,
  required,
  rules,
  toNumber,
} from "@/lib/form";

describe("form rules", () => {
  it("returns the first failing rule", () => {
    const validate = rules(required(), maxLength(3));
    expect(validate({ value: "" })).toEqual({ message: "Ce champ est obligatoire." });
    expect(validate({ value: "abcd" })?.message).toContain("3");
    expect(validate({ value: "abc" })).toBeUndefined();
  });

  it("validates lengths and patterns", () => {
    expect(lengthBetween(2, 4, "len")("a")).toBe("len");
    expect(lengthBetween(2, 4, "len")("abc")).toBeUndefined();
    expect(matches(/^[A-Z]+$/, "pattern")("abc")).toBe("pattern");
    expect(matches(/^[A-Z]+$/, "pattern")("")).toBeUndefined();
    expect(maxLength(2, "custom")("abc")).toBe("custom");
  });

  it("validates email addresses", () => {
    expect(email()("driver@example.test")).toBeUndefined();
    expect(email()("")).toBeUndefined();
    expect(email()("not-an-email")).toBeDefined();
    expect(email()(`${"a".repeat(320)}@x.fr`)).toBeDefined();
  });

  it("validates numbers", () => {
    expect(numeric()("12.5")).toBeUndefined();
    expect(numeric()(" ")).toBeUndefined();
    expect(numeric()("abc")).toBeDefined();
    expect(integerBetween(1, 10, "int")("5")).toBeUndefined();
    expect(integerBetween(1, 10, "int")("5.5")).toBe("int");
    expect(integerBetween(1, 10, "int")("11")).toBe("int");
    expect(integerBetween(1, 10, "int")("")).toBe("int");
    expect(optionalNumber(0, 100)("")).toBeUndefined();
    expect(optionalNumber(0, 100)("50")).toBeUndefined();
    expect(optionalNumber(0, 100)("101")).toBeDefined();
    expect(optionalNumber(0, 100)("x")).toBeDefined();
  });

  it("converts values", () => {
    expect(toNumber("12")).toBe(12);
    expect(toNumber(7)).toBe(7);
    expect(toNumber("")).toBeUndefined();
    expect(toNumber(null)).toBeUndefined();
    expect(toNumber("abc")).toBeUndefined();
    expect(asText(null)).toBe("");
    expect(asText(5)).toBe("5");
  });

  it("flags touched invalid fields only", () => {
    expect(isInvalid({ state: { meta: { isTouched: true, isValid: false } } })).toBe(true);
    expect(isInvalid({ state: { meta: { isTouched: false, isValid: false } } })).toBe(false);
    expect(isInvalid({ state: { meta: { isTouched: true, isValid: true } } })).toBe(false);
  });
});
