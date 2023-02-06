import { Card, useOdeClient } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { type IResource } from "ode-ts-client";

import "dayjs/locale/de";
import "dayjs/locale/es";
import "dayjs/locale/pt";
import "dayjs/locale/fr";
import "dayjs/locale/it";

dayjs.extend(relativeTime);

export default function ResourcesList(): JSX.Element | null {
  const { appCode, currentLanguage, session } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const { resources, openResource, deselect, select, isResourceSelected } =
    useExplorerStore((state) => state);

  function toggleSelect(resource: IResource) {
    if (isResourceSelected(resource)) {
      deselect([resource.id], "resource");
    } else {
      select([resource.id], "resource");
    }
  }

  function resourceIsShared(shared: any): boolean {
    return shared && shared.length >= 1;
  }

  return resources.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resources.map((resource: IResource) => {
        const { assetId, creatorName, name, thumbnail, updatedAt, shared } =
          resource;

        const time = dayjs(updatedAt).locale(currentLanguage).fromNow();

        return (
          <li className="g-col-4" key={assetId}>
            <Card
              appCode={appCode}
              className="c-pointer"
              creatorName={creatorName}
              isPublic={resource.public}
              isSelected={isResourceSelected(resource)}
              isShared={resourceIsShared(shared)}
              name={name}
              onOpen={() => openResource(assetId)}
              onSelect={() => toggleSelect(resource)}
              resourceSrc={thumbnail}
              updatedAt={time}
              userSrc={session?.avatarUrl}
            />
          </li>
        );
      })}
    </ul>
  ) : null;
}
