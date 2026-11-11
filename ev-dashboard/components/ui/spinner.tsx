import { t } from "@/lib/i18n";
import { cn } from "cn";
import { IconLoader } from "@tabler/icons-react";

function Spinner({ className, ...props }: React.ComponentProps<"svg">) {
  return (
    <IconLoader
      data-slot="spinner"
      role="status"
      aria-label={t("Chargement")}
      className={cn("size-4 animate-spin", className)}
      {...props}
    />
  );
}

export { Spinner };
