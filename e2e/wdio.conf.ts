import { fileURLToPath } from "node:url";
import { addAttachment } from "@wdio/allure-reporter";
import { APP_ID, resolveApkPath } from "./src/support/config.ts";
import { attachFailureScreenshot } from "./src/support/evidence.ts";

// Appium detects the project-local driver (an exact devDependency) when APPIUM_HOME is this directory.
process.env["APPIUM_HOME"] ??= fileURLToPath(new URL("./", import.meta.url));

export const config: WebdriverIO.Config = {
  runner: "local",
  specs: ["./src/tests/**/*.e2e.ts"],
  maxInstances: 1,
  logLevel: "info",
  framework: "mocha",
  mochaOpts: { timeout: 60_000 },
  waitforTimeout: 10_000,
  reporters: [
    "spec",
    [
      "allure",
      {
        outputDir: "./allure-results",
        disableWebdriverStepsReporting: true,
        disableWebdriverScreenshotsReporting: true,
      },
    ],
  ],
  services: [["appium", { args: { log: "./logs/appium.log" } }]],
  capabilities: [
    {
      platformName: "Android",
      "appium:automationName": "UiAutomator2",
      "appium:app": resolveApkPath(process.env),
      "appium:appPackage": APP_ID,
      "appium:noReset": true,
      "appium:enforceAppInstall": true,
      "appium:language": "en",
      "appium:locale": "US",
      "appium:newCommandTimeout": 240,
    },
  ],

  // Isolation: clear the app data before every test, then relaunch it.
  beforeTest: async () => {
    await driver.execute("mobile: clearApp", { appId: APP_ID });
    await driver.activateApp(APP_ID);
  },

  afterTest: async (_test, _context, { passed }) => {
    await attachFailureScreenshot(passed, {
      takeScreenshot: () => driver.takeScreenshot(),
      attach: addAttachment,
    });
  },
};
