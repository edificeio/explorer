import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
/* import { configure } from "ode-ts-client/src/ts/configure/Framework";
import { session } from "ode-ts-client/src/ts/session/Framework"; */

// import i18n (needs to be bundled ;))
import App from "./app/App";
import "./i18n";
import "./index.css";

const rootElement = document.getElementById("root");
const root = createRoot(rootElement!);

root.render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);

/* const handleSession = () => {
  return session.initialize().then(() => {
    console.log("init session");
    return configure.initialize(null, null);
  });
}; */

/* session
  .initialize()
  .then(() => configure.initialize(null, null))
  .then(() => {
    root.render(
      <BrowserRouter>
        <App />
      </BrowserRouter>,
    );

    //root.render(<App />);
  }); */

/* session.initialize().then(() => {
  root.render(
    <BrowserRouter>
      <App />
    </BrowserRouter>,
  );
}); */
