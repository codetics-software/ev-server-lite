import { cn } from "@/lib/utils";
import Image from "next/image";

export function CodeticsMark({ className }: { className?: string }) {
  return (
    <Image
      src="/codetics-logo.svg"
      alt=""
      width={35}
      height={28}
      className={cn("h-7 w-auto shrink-0", className)}
    />
  );
}
