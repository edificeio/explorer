import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
// TODO: Global export
import * as dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
// TODO
import { IResource } from "ode-ts-client";

dayjs.extend(relativeTime);

export default function ResourcesList() {
  const { app, session } = useExplorerContext();
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

  function resourceIsShared(shared: any): boolean {
    return shared.length >= 1;
  }

  return resources.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resources.map((resource: IResource) => {
        const { assetId, creatorName, name, thumbnail, shared } = resource;

        console.log(typeof shared);

        return (
          <li className="g-col-4" key={assetId}>
            <Card
              appCode={appCode}
              className="c-pointer"
              creatorName={creatorName}
              isSelected={isResourceSelected(resource)}
              isPublic={resource.public}
              isShared={resourceIsShared(shared)}
              name={name}
              onOpen={() => openSingleResource(assetId)}
              onSelect={() => toggleSelect(resource)}
              userSrc={session?.avatarUrl}
              resourceSrc={thumbnail}
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <img
      src={`/assets/themes/ode-bootstrap/images/emptyscreen/illu-${appCode}.svg`}
      alt="application emptyscreen"
      className="mx-auto"
      style={{ maxWidth: "50%" }}
    />
  );
}
