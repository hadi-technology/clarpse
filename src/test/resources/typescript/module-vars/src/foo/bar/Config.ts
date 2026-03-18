export const API_URL = "https://example.test";
let retryCount = 3;
var legacyFlag = true;

export function ping(): string {
  return API_URL;
}
