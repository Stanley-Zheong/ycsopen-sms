import { defineConfig } from "../../../../web/node_modules/@playwright/test";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const localChromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const repositoryRoot = resolve(__dirname, "../../../..");
const sourcePaths = [
  ".planning/phases/02-console-design-system-prototype-foundation/TEST-MATRIX.md",
  ".planning/phases/02-console-design-system-prototype-foundation/UI-ELEMENTS.md",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/prototype.spec.ts",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/playwright.config.ts",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/prototype.html",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/console-design.pen",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/tokens.css",
  ".planning/phases/02-console-design-system-prototype-foundation/design-output/ycsan-style-snapshot.json",
  ".planning/phases/02-console-design-system-prototype-foundation/EVIDENCE/ycsan-reference-1440x900.png",
];
const sourceSha256 = Object.fromEntries(sourcePaths.map((relative) => [
  relative,
  createHash("sha256").update(readFileSync(resolve(repositoryRoot, relative))).digest("hex"),
]));

// Phase 2 deliberately validates only the Chrome installation already present on this machine.
export default defineConfig({
  metadata: {
    phase: "02-console-design-system-prototype-foundation",
    browser: "Google Chrome",
    executablePath: localChromePath,
    viewport: { width: 1440, height: 900 },
    sourceSha256,
  },
  testDir: ".",
  testMatch: "prototype.spec.ts",
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [
    ["json", { outputFile: "../EVIDENCE/runs/phase02-latest/playwright-report.json" }],
    ["line"],
  ],
  outputDir: "../EVIDENCE/runs/phase02-latest/artifacts",
  use: {
    launchOptions: { executablePath: localChromePath },
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "off",
  },
});
