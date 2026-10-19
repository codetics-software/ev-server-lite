export type Tariff = {
  currency: string;
  energyPrice: number;
  timePrice: number;
  flatPrice: number;
};
export type PublicEvse = {
  id: string;
  workspace: string;
  workspaceSlug: string;
  name: string;
  stationId: string;
  connectorId: number;
  connected: boolean;
  status: string;
  available: boolean;
  tariff: Tariff;
  minimumCreditMinor: number;
  maximumCreditMinor: number;
};
export type Evse = {
  id: string;
  name: string;
  stationId: string;
  connectorId: number;
  pricingDefinitionId: string;
  published: boolean;
  portalUrl: string;
};
