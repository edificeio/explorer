import { Layout, useOdeClient, LoadingScreen } from "@edifice-ui/react";

import { getExplorerConfig } from "../getExplorerConfig";
import Explorer from "~/Explorer";

function Root() {
  const { init } = useOdeClient();

  const getHTMLConfig = getExplorerConfig();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Layout>
      <Explorer config={getHTMLConfig} />
    </Layout>
  );
}

export default Root;
