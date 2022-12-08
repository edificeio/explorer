// import { StrictMode } from "react";
import OdeProvider from "@contexts/OdeContext";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

// import i18n (needs to be bundled ;))
import App from "./app/App";
import "./i18n";

const rootElement = document.getElementById("root");
const root = createRoot(rootElement!);

root.render(
  <BrowserRouter>
    <OdeProvider>
      <App />
    </OdeProvider>
  </BrowserRouter>,
);
