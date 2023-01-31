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

  const {
    state: { resources },
    openSingleResource,
    selectResource,
    deselectResource,
    isResourceSelected,
  } = useExplorerContext();

  function toggleSelect(resource: IResource): void {
    if (isResourceSelected(resource)) {
      deselectResource(resource);
    } else {
      selectResource(resource);
    }
  }

  function resourceIsShared(shared: any): boolean {
    return Array.isArray(shared) && shared.length >= 1;
  }

  return resources.length ? (
    <ul className="grid ps-0 list-unstyled">
      {resources.map((resource: IResource) => {
        const { assetId, creatorName, name, thumbnail, updatedAt, shared } =
          resource;

        const time = dayjs(updatedAt).locale(currentLanguage).fromNow();

        console.log(updatedAt, time);

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
              onOpen={() => openSingleResource(assetId)}
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
