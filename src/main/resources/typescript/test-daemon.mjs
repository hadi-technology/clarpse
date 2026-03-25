// Test the daemon logic in isolation
const fs = require("fs");

const tsconfigWithCommas = `{
  "extends": "@adonisjs/tsconfig/tsconfig.client.json",
  "compilerOptions": {
    "baseUrl": ".",
    "module": "ESNext",
    "jsx": "react-jsx",
    "paths": {
      "~/*": ["./*"],
    },
  },
  "include": ["./**/*.ts"],
}`;

// Test the normalization
const readAndNormalizeConfig = (content) => {
  if (content === undefined) return undefined;
  const normalized = content.replace(/,(\s*[}\]])/g, "$1");
  return normalized;
};

const normalized = readAndNormalizeConfig(tsconfigWithCommas);
console.log("Normalized:", normalized);

try {
  const parsed = JSON.parse(normalized);
  console.log("Successfully parsed!");
  console.log("Keys:", Object.keys(parsed));
} catch (e) {
  console.log("Failed to parse:", e.message);
}
