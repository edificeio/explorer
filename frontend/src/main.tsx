// import { StrictMode } from "react";
import OdeProvider, { OdeProviderParams } from "@contexts/OdeContext";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

// import i18n (needs to be bundled ;))
import App from "./app/App";
import "./i18n";

const rootElement = document.querySelector<HTMLElement>("[data-ode-app]");
if (rootElement && rootElement.dataset && rootElement.dataset.odeApp) {
  const { odeApp } = rootElement.dataset;
  const params: OdeProviderParams = { app: "" };
  // Inject params (JSON object or string) read from index.html in OdeProvider
  try {
    const p = JSON.parse(odeApp);
    Object.assign(params, p);
  } catch {
    params.app = odeApp;
  }

  createRoot(rootElement!).render(
    <BrowserRouter>
      <OdeProvider params={params}>
        <App />
      </OdeProvider>
    </BrowserRouter>,
  );
} else {
  // HTTP 500 screen ?
}
