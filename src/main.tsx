import { OdeClientProvider } from "@ode-react-ui/core";
import { type OdeProviderParams } from "@ode-react-ui/core/dist/OdeClientProvider/OdeClientProps";
import {
  APP,
  ConfigurationFrameworkFactory,
  NotifyFrameworkFactory,
  SessionFrameworkFactory,
  TransportFrameworkFactory,
  type App as AppName,
} from "ode-ts-client";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./app/App";

const rootElement = document.querySelector<HTMLElement>("[data-ode-app]");
const root = document.getElementById("root");

const sessionFramework = SessionFrameworkFactory.instance();
const configurationFramework = ConfigurationFrameworkFactory.instance();
const notifyFramework = NotifyFrameworkFactory.instance();
const { http } = TransportFrameworkFactory.instance();

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

createRoot(root!).render(
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
  </BrowserRouter>,
);
