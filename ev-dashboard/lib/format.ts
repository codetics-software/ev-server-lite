export function display(value: unknown): string {
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  if (typeof value === "boolean") {
    return value ? "Yes" : "No";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}
export function date(value: unknown): string {
  if (!value) {
    return "—";
  }
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.valueOf())
    ? display(value)
    : new Intl.DateTimeFormat("en-GB", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(parsed);
}
