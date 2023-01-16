import { useOdeContext } from "@contexts/index";
import { Header, Main } from "@ode-react-ui/core";
import { clsx } from "@shared/config/index";

import Explorer from "./Explorer";

function App() {
  /* ode context */
  const { session, is1D, themeBasePath } = useOdeContext();

  if (!session || session.notLoggedIn) {
    return (
      <div>
        <a href="http://localhost:8090/" target="_blank" rel="noreferrer">
          S'identifier
        </a>
        sur le backend...
      </div>
    );
  }

  return (
    <div className="App">
      <Header is1d={is1D} src={`${themeBasePath}/img/illustrations/logo.png`} />
      <Main
        className={clsx("container-fluid bg-white", {
          "rounded-4 border": is1D,
          "mt-24": is1D,
        })}
      >
        <Explorer />
      </Main>
    </div>
  );
}

export default App;
