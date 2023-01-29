import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
import { useLuxon } from "@ode-react-ui/hooks";
import { IResource, ISession, IWebApp } from "ode-ts-client";

export default function ResourcesList({
  session,
  app,
  currentLanguage,
}: {
  session: ISession;
  app: IWebApp | undefined;
  currentLanguage: string;
}) {
  const appCode = app?.address.replace("/", "");

  const { getUpdatedDate } = useLuxon(currentLanguage);

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
        const { assetId, creatorName, name, thumbnail, updatedAt, shared } =
          resource;

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
              updatedAt={getUpdatedDate(updatedAt)}
              userSrc={session?.avatarUrl}
            />
          </li>
        );
      })}
    </ul>
  ) : null;
}
