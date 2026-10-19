export type NetworkOverview = {
  stations: { total: number; online: number };
  transactions: { total: number; active: number; energy_kwh: number };
  connectors: { status: string; count: number }[];
};
