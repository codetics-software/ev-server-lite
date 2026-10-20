"use client";

import { toast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api";
import { useState } from "react";

export function notify(title: string, description?: string) {
  toast.add({ title, description, type: "success" });
}

export function notifyError(title: string, description?: string) {
  toast.add({ title, description, type: "error", priority: "high" });
}

export function useAction() {
  const [pending, setPending] = useState<string | null>(null);

  async function run<T>(
    key: string,
    action: () => Promise<T>,
    options: {
      success?: string | ((result: T) => string);
      failure?: string;
      onDone?: (result: T) => void;
    } = {},
  ): Promise<T | undefined> {
    setPending(key);
    try {
      const result = await action();
      if (options.success) {
        notify(typeof options.success === "function" ? options.success(result) : options.success);
      }
      options.onDone?.(result);
      return result;
    } catch (error) {
      notifyError(
        options.failure ?? "L’opération n’a pas abouti",
        errorMessage(error, "Réessayez dans quelques instants."),
      );
      return undefined;
    } finally {
      setPending(null);
    }
  }

  return { pending, run, busy: pending !== null };
}
