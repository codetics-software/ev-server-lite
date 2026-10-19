import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { date } from "@/lib/format";
import type { StationSummary } from "./station-types";

export function StationsTable({
  stations,
  onSelect,
}: {
  stations: StationSummary[];
  onSelect: (station: StationSummary) => void;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Station</TableHead>
          <TableHead>Connection</TableHead>
          <TableHead>Manufacturer</TableHead>
          <TableHead>Last contact</TableHead>
          <TableHead>
            <span className="sr-only">Actions</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {stations.map((station) => (
          <TableRow key={station.id}>
            <TableCell>
              <span className="block font-medium">{station.name}</span>
              <span className="text-xs text-muted-foreground">{station.id}</span>
            </TableCell>
            <TableCell>
              <Badge variant={station.connected ? "secondary" : "outline"}>
                {station.connected ? "Connected" : "Offline"}
              </Badge>
            </TableCell>
            <TableCell>
              <span className="block">{station.vendor ?? "—"}</span>
              <span className="text-xs text-muted-foreground">{station.model ?? ""}</span>
            </TableCell>
            <TableCell className="text-muted-foreground">{date(station.last_seen)}</TableCell>
            <TableCell className="text-right">
              <Button variant="ghost" size="sm" onClick={() => onSelect(station)}>
                View
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
