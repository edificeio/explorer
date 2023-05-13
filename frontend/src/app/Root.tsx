import Explorer from "@app/Explorer";
import { Layout } from "@ode-react-ui/advanced";
import { useOdeClient, LoadingScreen } from "@ode-react-ui/core";

function Root() {
  const { init } = useOdeClient();

  if (!init) return <LoadingScreen position={false} />;

  return init ? (
    <Layout>
      <Explorer />
    </Layout>
  ) : null;
}

export default Root;
