import React, { StrictMode } from "react";

import { OdeClientProvider } from "@ode-react-ui/core";
import {
  configurationFramework,
  http,
  notifyFramework,
  sessionFramework,
} from "@shared/constants";
import { getAppParams } from "@shared/utils/getAppParams";
import { createRoot } from "react-dom/client";

import App from "./app/App";

const root = document.getElementById("root");

if (process.env.NODE_ENV !== "production") {
  import("@axe-core/react").then((axe) => {
    axe.default(React, root, 1000);
  });
}

createRoot(root!).render(
  <StrictMode>
    <OdeClientProvider
      framework={{
        sessionFramework,
        configurationFramework,
        notifyFramework,
        http,
      }}
      params={getAppParams()}
    >
      <App />
    </OdeClientProvider>
  </StrictMode>,
);
