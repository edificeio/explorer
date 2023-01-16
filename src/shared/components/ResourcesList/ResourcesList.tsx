import { useExplorerContext, useOdeContext } from "@contexts/index";
import { IResource } from "ode-ts-client";

import { FakeCard } from "../Card";

export default function ResourcesList() {
  const { currentApp } = useOdeContext();

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
    <ul className="grid ps-0">
      {resources.map((resource: IResource) => {
        return (
          <FakeCard
            {...resource}
            key={resource.assetId}
            currentApp={currentApp}
            selected={isResourceSelected(resource)}
            onClick={() => toggleSelect(resource)}
          />
        );
      })}
    </ul>
  ) : (
    <p>Aucune resource</p>
  );
}
