import React, { useCallback } from "react";

import { Button, Card, useOdeClient } from "@edifice-ui/react";
import { useSpring, animated } from "@react-spring/web";
import clsx from "clsx";
import { type ID, type IResource } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { useSearchContext } from "~/services/queries";
import { dayjs } from "~/shared/config";
import { isResourceShared } from "~/shared/utils/isResourceShared";
import {
  useStoreActions,
  useResourceIds,
  useSelectedResources,
  useSearchParams,
  useIsTrash,
} from "~/store";

const ResourcesList = (): JSX.Element | null => {
  const { currentApp, currentLanguage, appCode } = useOdeClient();
  const { t } = useTranslation();

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

  const isTrashFolder = useIsTrash();

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  const currentMaxIdx =
    searchParams.pagination.startIdx + searchParams.pagination.pageSize;
  const hasMoreResources =
    currentMaxIdx < (searchParams.pagination.maxIdx || 0);

  const handleNextPage = useCallback(() => {
    fetchNextPage();
  }, []);

  const clickOnResource = (resource: IResource) => {
    if (isTrashFolder) {
      setResourceIsTrash(true);
      setResourceIds([resource.id]);
      setSelectedResources([resource]);
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
              const { id, creatorName, creatorId, name, thumbnail, updatedAt } =
                resource;

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
                    messagePublic={t("tooltip.public", { ns: appCode })}
                    messageShared={t("tooltip.shared", { ns: appCode })}
                    name={name}
                    onOpen={() => clickOnResource(resource)}
                    onSelect={() => toggleSelect(resource)}
                    resourceSrc={thumbnail}
                    updatedAt={time}
                    userSrc={`/userbook/avatar/${creatorId}`}
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
            {t("explorer.see.more")}
          </Button>
        </div>
      )}
      {/* {hasMoreResources && <LoadMore />} */}
    </React.Fragment>
  );
};

export default ResourcesList;
