import { Layout, useOdeClient, LoadingScreen } from "@edifice-ui/react";

import { getExplorerConfig } from "../../config/getExplorerConfig";
import Explorer from "~/components/Explorer";

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
