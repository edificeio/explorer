import React, { useCallback } from 'react';

import { ISearchResults, type ID, type IResource } from '@edifice.io/client';
import { Button, useDate, useEdificeClient } from '@edifice.io/react';
import { animated, useSpring } from '@react-spring/web';
import { InfiniteData } from '@tanstack/react-query';
import clsx from 'clsx';
import { useTranslation } from 'react-i18next';

import {
  useIsTrash,
  useResourceIds,
  useSearchParams,
  useSelectedResources,
  useStoreActions,
} from '~/store';
import ResourceCard from './ResourceCard';

const ResourcesList = ({
  data,
  fetchNextPage,
}: {
  data: InfiniteData<ISearchResults> | undefined;
  isFetching: boolean;
  fetchNextPage: () => void;
}): JSX.Element | null => {
  const { currentApp } = useEdificeClient();
  const { t } = useTranslation();
  const { fromNow } = useDate();

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleOnResourceClick = (resource: IResource) => {
    if (isTrashFolder) {
      setResourceIsTrash(true);
      setResourceIds([resource.id]);
      setSelectedResources([resource]);
    } else {
      openResource(resource);
    }
  };

  async function handleOnSelectToggle(resource: IResource) {
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
      return;
    }
    setResourceIds([...resourceIds, resource.id]);
    setSelectedResources([...selectedResources, resource]);
  }

  const classes = clsx('grid ps-0 list-unstyled');

  return (
    <React.Fragment>
      <animated.ul className={classes}>
        {data?.pages.map((page: { resources: IResource[] }, index: number) => (
          <React.Fragment key={index}>
            {page.resources.map((resource: IResource) => {
              const { id, updatedAt } = resource;

              const time = fromNow(updatedAt);

              return (
                <animated.li
                  className="g-col-4"
                  key={id}
                  style={{
                    position: 'relative',
                    ...springs,
                  }}
                >
                  <ResourceCard
                    app={currentApp}
                    resource={resource}
                    time={time}
                    isSelectable={true}
                    isSelected={resourceIds.includes(resource.id)}
                    onClick={(event) => {
                      event.stopPropagation();
                      handleOnResourceClick(resource);
                    }}
                    onSelect={() => handleOnSelectToggle(resource)}
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
            {t('explorer.see.more')}
          </Button>
        </div>
      )}
    </React.Fragment>
  );
};

export default ResourcesList;
