import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
// TODO: Global export
import * as dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
// TODO
import { IResource, ISession, IWebApp } from "ode-ts-client";

dayjs.extend(relativeTime);

export default function ResourcesList({
  session,
  app,
}: {
  session: ISession;
  app: IWebApp | undefined;
}) {
  // const currentApp = useApp();
  const appCode = app?.address.replace("/", "");

  const {
    state: { resources },
    openSingleResource,
    selectResource,
    deselectResource,
    isResourceSelected,
  } = useExplorerContext();

  function toggleSelect(resource: IResource) {
    if (isResourceSelected(resource)) {
      deselectResource(resource);
    } else {
      selectResource(resource);
    }
  }

  return resources.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resources.map((resource: IResource, index: number) => {
        /* const style = {
          "--ode-enter-delay": index + "00ms",
        } as React.CSSProperties; */
        return (
          <li className="g-col-4" key={resource.assetId}>
            <Card
              // style={style}
              appCode={appCode}
              className="c-pointer"
              creatorName={resource.creatorName}
              // isAnimated
              isSelected={isResourceSelected(resource)}
              name={resource.name}
              onOpen={() => openSingleResource(resource.assetId)}
              onSelect={() => toggleSelect(resource)}
              updatedAt={dayjs(resource.updatedAt).fromNow()}
              userSrc={session?.avatarUrl}
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <img
      src={`/assets/themes/ode-bootstrap/images/emptyscreen/illu-${appCode}.svg`}
      alt="application emptyscreen"
    />
  );
}
