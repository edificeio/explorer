import { Layout, LoadingScreen, useEdificeClient } from '@edifice.io/react';

import Explorer from '~/components/Explorer';
import { getExplorerConfig } from '~/config';

function Root() {
  const { init } = useEdificeClient();

  const getHTMLConfig = getExplorerConfig();

  if (!init) return <LoadingScreen position={false} />;

  return (
    <Layout>
      <Explorer config={getHTMLConfig} />
    </Layout>
  );
}

export default Root;
