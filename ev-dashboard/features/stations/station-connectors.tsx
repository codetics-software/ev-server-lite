"use client";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { display } from "@/lib/api";

import type { StationConnector } from "./station-types";

export function StationConnectors({ connectors }: { connectors: StationConnector[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Connecteur</TableHead>
          <TableHead>État</TableHead>
          <TableHead>Erreur</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {connectors.map((connector) => (
          <TableRow key={String(connector.connector_id)}>
            <TableCell>{display(connector.connector_id)}</TableCell>
            <TableCell>
              <Badge variant="outline">{display(connector.status)}</Badge>
            </TableCell>
            <TableCell>{display(connector.error_code)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
