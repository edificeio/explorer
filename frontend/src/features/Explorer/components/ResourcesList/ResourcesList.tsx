import React, { useCallback } from "react";

import { Button, Card } from "@ode-react-ui/components";
import { useOdeClient, useUser } from "@ode-react-ui/core";
import { useSpring, animated } from "@react-spring/web";
import clsx from "clsx";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { type ID, type IResource } from "ode-ts-client";

import { useSearchContext } from "~/services/queries";
import { isResourceShared } from "~/shared/utils/isResourceShared";
import {
  useStoreActions,
  useResourceIds,
  useSelectedResources,
  useSearchParams,
} from "~/store";

import "dayjs/locale/de";
import "dayjs/locale/es";
import "dayjs/locale/pt";
import "dayjs/locale/fr";
import "dayjs/locale/it";

dayjs.extend(relativeTime);

const ResourcesList = (): JSX.Element | null => {
  const { currentApp, currentLanguage, i18n } = useOdeClient();
  const { avatar } = useUser();

  const { data, isFetching, fetchNextPage } = useSearchContext();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const searchParams = useSearchParams();
  const resourceIds = useResourceIds();
  const selectedResources = useSelectedResources();
  const {
    setSelectedResources,
    setResourceIds,
    openResource,
    setResourceIsTrash,
  } = useStoreActions();

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  const currentMaxIdx =
    searchParams.pagination.startIdx + searchParams.pagination.pageSize - 1;
  const hasMoreResources =
    currentMaxIdx < (searchParams.pagination.maxIdx || 0);

  const handleNextPage = useCallback(() => {
    fetchNextPage();
  }, []);

  const clickOnResource = (resource: IResource) => {
    if (resource.trashed) {
      setResourceIsTrash(true);
      setResourceIds([resource.id]);
    } else {
      openResource(resource);
    }
  };

  function toggleSelect(resource: IResource) {
    if (resourceIds.includes(resource.id)) {
      setResourceIds(
        resourceIds.filter(
          (selectedResource: ID) => selectedResource !== resource.id,
        ),
      );
      setSelectedResources(
        selectedResources.filter(
          (selectedResource) => selectedResource.id !== resource.id,
        ),
      );
    } else {
      setResourceIds([...resourceIds, resource.id]);
      setSelectedResources([...selectedResources, resource]);
    }
  }

  const classes = clsx("grid ps-0 list-unstyled");

  return (
    <React.Fragment>
      <animated.ul className={classes}>
        {data?.pages.map((page: { resources: IResource[] }, index: number) => (
          // eslint-disable-next-line react/no-array-index-key
          <React.Fragment key={index}>
            {page.resources.map((resource: IResource) => {
              const { id, creatorName, name, thumbnail, updatedAt } = resource;

              const isShared = isResourceShared(resource);

              const time = dayjs(updatedAt)
                .locale(currentLanguage as string)
                .fromNow();

              return (
                <animated.li
                  className="g-col-4"
                  key={id}
                  style={{
                    ...springs,
                  }}
                >
                  <Card
                    app={currentApp}
                    className="c-pointer"
                    creatorName={creatorName}
                    isPublic={resource.public}
                    isSelected={resourceIds.includes(resource.id)}
                    isLoading={isFetching}
                    isShared={isShared}
                    messagePublic={i18n("tooltip.public")}
                    messageShared={i18n("tooltip.shared")}
                    name={name}
                    onOpen={() => clickOnResource(resource)}
                    onSelect={() => toggleSelect(resource)}
                    resourceSrc={thumbnail}
                    updatedAt={time}
                    userSrc={avatar}
                  />
                </animated.li>
              );
            })}
          </React.Fragment>
        ))}
      </animated.ul>
      {hasMoreResources && (
        <div className="d-grid gap-2 col-4 mx-auto my-24">
          <Button
            type="button"
            color="secondary"
            variant="filled"
            onClick={handleNextPage}
          >
            {i18n("explorer.see.more")}
          </Button>
        </div>
      )}
    </React.Fragment>
  );
};

export default ResourcesList;
