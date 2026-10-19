import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { date } from "@/lib/format";
import type { ChargingSession } from "./session-types";

export function SessionsTable({ sessions }: { sessions: ChargingSession[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Session</TableHead>
          <TableHead>Station</TableHead>
          <TableHead>State</TableHead>
          <TableHead>Started</TableHead>
          <TableHead>Stopped</TableHead>
          <TableHead className="text-right">Energy · kWh</TableHead>
          <TableHead>Authorization</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {sessions.map((session) => (
          <TableRow key={session.id}>
            <TableCell className="tabular-nums">#{session.id}</TableCell>
            <TableCell>
              <span className="block font-medium">{session.station_id}</span>
              <span className="text-xs text-muted-foreground">
                Connector {session.connector_id}
              </span>
            </TableCell>
            <TableCell>
              <Badge variant={session.stopped_at ? "outline" : "secondary"}>
                {session.stopped_at ? "Completed" : "Active"}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">{date(session.started_at)}</TableCell>
            <TableCell className="text-muted-foreground">{date(session.stopped_at)}</TableCell>
            <TableCell className="text-right tabular-nums">
              {session.energy_kwh === null
                ? "—"
                : Number(session.energy_kwh).toLocaleString("en-GB", {
                    minimumFractionDigits: 3,
                    maximumFractionDigits: 3,
                  })}
            </TableCell>
            <TableCell className="text-muted-foreground">{session.authorization_status}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
