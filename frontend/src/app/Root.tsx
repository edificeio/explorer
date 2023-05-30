import { LoadingScreen, Layout } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";

import Explorer from "~/app/Explorer";

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
