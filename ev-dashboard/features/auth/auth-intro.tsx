import { CodeticsMark } from "@/components/codetics-mark";
import Link from "next/link";

export function AuthIntro() {
  return (
    <aside className="auth-intro">
      <Link href="https://codetics.fr" className="flex items-center gap-3 text-lg font-semibold">
        <CodeticsMark />
        Codetics
      </Link>
      <div className="flex max-w-sm flex-col gap-6">
        <div>
          <h1>Charging network supervision.</h1>
          <p>Stations, charging sessions and remote operations in one workspace.</p>
        </div>
        <dl className="flex flex-col gap-5 text-sm">
          <div>
            <dt className="font-medium">Station operations</dt>
            <dd className="mt-1 text-muted-foreground">
              Connections, connector states and OCPP commands.
            </dd>
          </div>
          <div>
            <dt className="font-medium">Session history</dt>
            <dd className="mt-1 text-muted-foreground">
              Authorization, timestamps and measured energy.
            </dd>
          </div>
          <div>
            <dt className="font-medium">Your network</dt>
            <dd className="mt-1 text-muted-foreground">Sites, people and charging access.</dd>
          </div>
        </dl>
      </div>
      <span className="text-xs text-muted-foreground">Kevin Vavelin · Codetics</span>
    </aside>
  );
}
