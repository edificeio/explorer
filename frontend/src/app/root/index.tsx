import { Explorer } from "@edifice-ui/explorer";
import { Layout, useOdeClient, LoadingScreen } from "@edifice-ui/react";

import { explorerConfig } from "../config";
import { getExplorerConfig } from "../getExplorerConfig";

function Root() {
  const { init } = useOdeClient();

  const getHTMLConfig = getExplorerConfig();

  console.log({ getHTMLConfig });

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Layout>
      <Explorer config={getHTMLConfig ?? explorerConfig} />
    </Layout>
  );
}

export default Root;
