/** Values are the ones the API expects, so they travel unchanged. */
export const TEMPERATURE_UNITS = ['G_CEL', 'G_FAH'] as const;

export type TemperatureUnit = (typeof TEMPERATURE_UNITS)[number];
