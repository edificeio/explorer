import { Toast, Main, useOdeClient } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";
import toast, { Toaster, resolveValue } from "react-hot-toast";
import { Link } from "react-router-dom";

import Explorer from "./Explorer";
import { Header } from "./Header";

function App() {
  const { session, theme, i18n } = useOdeClient();

  const is1d: boolean = theme?.is1D;
  const basePath: string = theme?.basePath;

  if (!session || session.notLoggedIn) {
    return (
      <div className="d-grid min-vh-100 align-items-center justify-content-center">
        <Link to="/auth/login" target="_blank" rel="noreferrer">
          S'identifier sur le backend...
        </Link>
      </div>
    );
  }

  const { dismiss } = toast;

  const toastOptions = {
    position: "top-right",
    gutter: 8,
  };

  return (
    <div className="App">
      <Header is1d={is1d} src={basePath} session={session} i18n={i18n} />
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1d,
          "mt-24": is1d,
        })}
      >
        <Explorer />
      </Main>
      <Toast
        Toaster={Toaster}
        resolveValue={resolveValue}
        dismiss={dismiss}
        i18n={i18n}
        toastOptions={toastOptions}
      />
    </div>
  );
}

export default App;
