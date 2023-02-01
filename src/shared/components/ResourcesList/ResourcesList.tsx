import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { IResource, ISession, IWebApp } from "ode-ts-client";

import "dayjs/locale/de";
import "dayjs/locale/es";
import "dayjs/locale/pt";
import "dayjs/locale/fr";
import "dayjs/locale/it";

dayjs.extend(relativeTime);

export default function ResourcesList({
  session,
  app,
  currentLanguage,
}: {
  session: ISession;
  app: IWebApp | undefined;
  currentLanguage: string;
}): JSX.Element | null {
  const appCode = app?.address.replace("/", "");

  const { resourceList, openResource, select, deselect, isResourceSelected } =
    useExplorerContext();

  function toggleSelect(resource: IResource): void {
    if (isResourceSelected(resource)) {
      deselect([resource.id], "resource");
    } else {
      select([resource.id], "resource");
    }
  }

  function resourceIsShared(shared: any): boolean {
    return shared && shared.length >= 1;
  }

  return resourceList.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resourceList.map((resource: IResource) => {
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
              onOpen={() => {
                openResource(assetId);
              }}
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
