// import { StrictMode } from "react";
import ExplorerContextProvider from "@contexts/ExplorerContext";
import OdeProvider, { OdeProviderParams } from "@contexts/OdeContext";
import { APP, App as AppName, RESOURCE } from "ode-ts-client";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./app/App";
// import "./i18n";

const rootElement = document.querySelector<HTMLElement>("[data-ode-app]");
if (rootElement && rootElement.dataset && rootElement.dataset.odeApp) {
  const { odeApp } = rootElement.dataset;
  const params: OdeProviderParams = { app: APP.PORTAL };
  // Inject params (JSON object or string) read from index.html in OdeProvider
  try {
    const p = JSON.parse(odeApp);
    Object.assign(params, p);
  } catch {
    params.app = odeApp as AppName;
  }

  createRoot(rootElement!).render(
    <BrowserRouter>
      <OdeProvider params={params}>
        <ExplorerContextProvider types={[RESOURCE.BLOG]}>
          <App />
        </ExplorerContextProvider>
      </OdeProvider>
    </BrowserRouter>,
  );
} else {
  // HTTP 500 screen ?
}
