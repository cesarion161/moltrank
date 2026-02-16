/**
 * Lightweight Anchor smoke test.
 *
 * Keeps `anchor test` deterministic in environments where a local validator
 * deployment may not be available, while still validating that build artifacts
 * and expected instruction definitions are present.
 */

const fs = require("fs");
const path = require("path");

function normalize(name) {
  return String(name).replace(/_/g, "").toLowerCase();
}

function main() {
  const idlPath = path.join(__dirname, "target", "idl", "moltrank.json");
  if (!fs.existsSync(idlPath)) {
    throw new Error(`IDL not found at ${idlPath}`);
  }

  const idl = JSON.parse(fs.readFileSync(idlPath, "utf8"));
  const instructions = Array.isArray(idl.instructions) ? idl.instructions : [];
  if (instructions.length === 0) {
    throw new Error("IDL has no instructions");
  }

  const actual = new Set(instructions.map((ix) => normalize(ix.name)));
  const expected = [
    "init_global_pool",
    "create_market",
    "init_round",
    "commit_vote",
    "settle_pair",
  ].map(normalize);

  for (const instruction of expected) {
    if (!actual.has(instruction)) {
      throw new Error(`Missing expected instruction in IDL: ${instruction}`);
    }
  }

  console.log("Anchor smoke test passed");
}

try {
  main();
} catch (err) {
  console.error("Anchor smoke test failed:", err);
  process.exit(1);
}
