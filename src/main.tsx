import React, { StrictMode } from "react";

import { OdeClientProvider, type OdeProviderParams } from "@ode-react-ui/core";
import {
  configurationFramework,
  http,
  notifyFramework,
  sessionFramework,
} from "@shared/constants";
import { APP, type App as AppName } from "ode-ts-client";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./app/App";

const rootElement = document.querySelector<HTMLElement>("[data-ode-app]");
const root = document.getElementById("root");

function getParams() {
  const params: OdeProviderParams = { app: APP.PORTAL };
  if (rootElement?.dataset?.odeApp) {
    const { odeApp } = rootElement.dataset;
    // Inject params (JSON object or string) read from index.html in OdeProvider
    try {
      const p = JSON.parse(odeApp);
      Object.assign(params, p);
    } catch {
      params.app = odeApp as AppName;
    }
  }
  return params;
}

if (process.env.NODE_ENV !== "production") {
  import("@axe-core/react").then((axe) => {
    axe.default(React, root, 1000);
  });
}

createRoot(root!).render(
  // <MotionConfig reducedMotion="user">
  <StrictMode>
    <BrowserRouter>
      <OdeClientProvider
        framework={{
          sessionFramework,
          configurationFramework,
          notifyFramework,
          http,
        }}
        params={getParams()}
      >
        <App />
      </OdeClientProvider>
    </BrowserRouter>
  </StrictMode>,
  // </MotionConfig>
);
