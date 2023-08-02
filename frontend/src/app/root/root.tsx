import { Layout, useOdeClient, LoadingScreen } from "@edifice-ui/react";

import Explorer from "~/app/explorer";

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
