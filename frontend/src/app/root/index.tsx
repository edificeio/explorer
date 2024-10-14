import { Layout, LoadingScreen, useOdeClient } from '@edifice-ui/react';

import Explorer from '~/components/Explorer';
import { getExplorerConfig } from '~/config';

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
