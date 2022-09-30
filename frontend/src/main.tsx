import { StrictMode } from "react";

import { createRoot } from "react-dom/client";

// import i18n (needs to be bundled ;))
import App from "./app/App";
import "./i18n";
import "./index.css";

const rootElement = document.getElementById("root");
const root = createRoot(rootElement!);

root.render(
  <StrictMode>
    <App />
  </StrictMode>,
);
