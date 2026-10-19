import type { FormField } from "../types";

export const networkFields: Record<string, FormField[]> = {
  companies: [
    { key: "name", label: "Company name", required: true },
    { key: "address", label: "Address" },
    { key: "vatNumber", label: "VAT number" },
  ],
  sites: [
    { key: "name", label: "Site name", required: true },
    { key: "companyId", label: "Company ID" },
    { key: "address", label: "Street address" },
    { key: "city", label: "City" },
    { key: "country", label: "Country" },
    { key: "latitude", label: "Latitude", type: "number" },
    { key: "longitude", label: "Longitude", type: "number" },
  ],
  "site-areas": [
    { key: "name", label: "Area name", required: true },
    { key: "siteId", label: "Site ID", required: true },
    { key: "maximumPowerKw", label: "Available power (kW)", type: "number" },
  ],
  assets: [
    { key: "name", label: "Asset name", required: true },
    {
      key: "type",
      label: "Asset type",
      options: ["METER", "SOLAR", "BATTERY", "BUILDING"],
    },
    { key: "siteAreaId", label: "Site area ID" },
    { key: "maximumPowerKw", label: "Power (kW)", type: "number" },
  ],
  cars: [
    { key: "name", label: "Vehicle name", required: true },
    { key: "registration", label: "Registration plate" },
    { key: "make", label: "Manufacturer" },
    { key: "model", label: "Model" },
    { key: "batteryKwh", label: "Battery capacity (kWh)", type: "number" },
  ],
  "charging-station-templates": [
    { key: "name", label: "Template name", required: true },
    { key: "vendor", label: "Manufacturer" },
    { key: "model", label: "Model" },
    { key: "connectorCount", label: "Connector count", type: "number" },
  ],
  "pricing-definitions": [
    { key: "name", label: "Definition name", required: true },
    { key: "currency", label: "Currency", required: true },
    { key: "energyPrice", label: "Price per kWh", type: "number" },
    { key: "timePrice", label: "Price per hour", type: "number" },
    { key: "flatPrice", label: "Session fee", type: "number" },
  ],
  settings: [
    { key: "name", label: "Setting name", required: true },
    { key: "value", label: "Value", required: true },
  ],
};
