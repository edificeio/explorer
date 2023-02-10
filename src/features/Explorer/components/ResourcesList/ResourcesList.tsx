import { Card, useOdeClient } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { motion } from "framer-motion";
import { type IResource } from "ode-ts-client";

import "dayjs/locale/de";
import "dayjs/locale/es";
import "dayjs/locale/pt";
import "dayjs/locale/fr";
import "dayjs/locale/it";

dayjs.extend(relativeTime);

export default function ResourcesList(): JSX.Element | null {
  const { app, currentLanguage, session } = useOdeClient();

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

  // * Framer Motion
  const list = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1,
      },
    },
  };

  // * Framer Motion
  const item = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
    },
  };

  return resources.length ? (
    <motion.ul
      initial="hidden"
      animate="show"
      variants={list}
      className="grid ps-0 list-unstyled mt-24"
    >
      {resources.map((resource: IResource) => {
        const { assetId, creatorName, name, thumbnail, updatedAt, shared } =
          resource;

        const time = dayjs(updatedAt).locale(currentLanguage).fromNow();

        return (
          <motion.li className="g-col-4" key={assetId} variants={item}>
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
            />
          </motion.li>
        );
      })}
    </motion.ul>
  ) : null;
}
