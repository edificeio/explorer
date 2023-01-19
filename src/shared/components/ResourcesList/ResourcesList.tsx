import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
import { useCurrentApp } from "@store/useOdeStore";
import { IResource } from "ode-ts-client";

export default function ResourcesList() {
  const currentApp = useCurrentApp();
  const appCode = currentApp?.address.replace("/", "");

  const {
    state: { resources },
    selectResource,
    deselectResource,
    isResourceSelected,
  } = useExplorerContext();

  function toggleSelect(item: IResource) {
    if (isResourceSelected(item)) {
      deselectResource(item);
    } else {
      selectResource(item);
    }
  }

  return resources.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resources.map((resource: IResource) => {
        return (
          <li className="g-col-4" key={resource.assetId}>
            <Card
              name={resource.name}
              creatorName={resource.creatorName}
              updatedAt={resource.updatedAt}
              appCode={appCode}
              isSelected={isResourceSelected(resource)}
              onClick={() => toggleSelect(resource)}
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <p>Aucune resource</p>
  );
}
