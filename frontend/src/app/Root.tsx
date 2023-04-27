import Explorer from "@app/Explorer";
import { Layout } from "@ode-react-ui/advanced";
import { useOdeClient } from "@ode-react-ui/core";
import { configurationFramework } from "@shared/constants";

function Root() {
  const { session } = useOdeClient();

  if (!session || session.notLoggedIn) {
    return (
      <div className="d-grid min-vh-100 align-items-center justify-content-center">
        <a href="/auth/login" target="_blank" rel="noreferrer">
          S'identifier sur le backend...
        </a>
      </div>
    );
  }

  return (
    <Layout configurationFramework={configurationFramework}>
      <Explorer />
    </Layout>
  );
}

export default Root;
