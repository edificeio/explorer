import { Card, useOdeClient } from "@ode-react-ui/core";
import { useSpring, animated } from "@react-spring/web";
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
  const { app, currentLanguage, session, i18n } = useOdeClient();

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

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  return resources.length ? (
    <animated.ul className="grid ps-0 list-unstyled mt-24">
      {resources.map((resource: IResource) => {
        const { assetId, creatorName, name, thumbnail, updatedAt, shared } =
          resource;

        const time = dayjs(updatedAt).locale(currentLanguage).fromNow();

        return (
          <animated.li
            className="g-col-4"
            key={assetId}
            style={{
              ...springs,
            }}
          >
            <Card
              app={app}
              className="c-pointer"
              creatorName={creatorName}
              isPublic={resource.public}
              isSelected={isResourceSelected(resource)}
              isShared={resourceIsShared(shared)}
              name={name}
              onOpen={async () => await openResource(assetId)}
              onSelect={() => toggleSelect(resource)}
              resourceSrc={thumbnail}
              updatedAt={time}
              userSrc={session?.avatarUrl}
              messagePublic={i18n("tooltip.public")}
              messageShared={i18n("tooltip.shared")}
            />
          </animated.li>
        );
      })}
    </animated.ul>
  ) : null;
}
