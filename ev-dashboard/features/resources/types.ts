export type FormField = {
  key: string;
  label: string;
  required?: boolean;
  type?: "text" | "email" | "number" | "checkbox" | "datetime-local";
  options?: string[];
};

export type ResourceColumn = readonly [key: string, label: string];
