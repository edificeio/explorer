import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
import { useCurrentApp } from "@store/useOdeStore";
// TODO: Global export
import * as dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
// TODO
import { IResource } from "ode-ts-client";

dayjs.extend(relativeTime);

export default function ResourcesList() {
  const currentApp = useCurrentApp();
  const appCode = currentApp?.address.replace("/", "");

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
      {resources.map((resource: IResource) => {
        return (
          <li className="g-col-4" key={resource.assetId}>
            <Card
              appCode={appCode}
              className="c-pointer"
              creatorName={resource.creatorName}
              isSelected={isResourceSelected(resource)}
              name={resource.name}
              onOpen={() => openSingleResource(resource.assetId)}
              onSelect={() => toggleSelect(resource)}
              updatedAt={dayjs(resource.updatedAt).fromNow()}
              src="https://media.istockphoto.com/id/1322277517/fr/photo/herbe-sauvage-dans-les-montagnes-au-coucher-du-soleil.jpg?s=612x612&w=0&k=20&c=tQ19uZQLlIFy8J6QWMyOL6lPt3pdSHBSDFHoXr1K_g0="
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <p>Aucune resource</p>
  );
}
