import React, { useEffect } from "react";

import { Card, useOdeClient } from "@ode-react-ui/core";
import { useInfiniteContext } from "@queries/index";
import { useSpring, animated } from "@react-spring/web";
import LoadMore from "@shared/components/LoadMore";
import useExplorerStore from "@store/index";
import clsx from "clsx";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { FOLDER, type IResource } from "ode-ts-client";
import { useInView } from "react-intersection-observer";

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
  const searchParams = useExplorerStore((state) => state.searchParams);
  const setResources = useExplorerStore((state) => state.setResources);
  const openResource = useExplorerStore((state) => state.openResource);
  const select = useExplorerStore((state) => state.select);
  const deselect = useExplorerStore((state) => state.deselect);
  const isResourceSelected = useExplorerStore(
    (state) => state.isResourceSelected,
  );
  const updateSearchParams = useExplorerStore(
    (state) => state.updateSearchParams,
  );
  const getCurrentFolderId = useExplorerStore(
    (state) => state.getCurrentFolderId,
  );

  const currentMaxIdx =
    searchParams.pagination.startIdx + searchParams.pagination.pageSize - 1;
  const hasMoreResources =
    currentMaxIdx < (searchParams.pagination.maxIdx || 0);

  /**
   * Custom hook for Infinite Scroll
   * @param searchParams searchParams
   * @param onSuccess
   * @returns data, fetchNextPage, isFetching
   */
  const { data, fetchNextPage, isFetching } = useInfiniteContext({
    searchParams: {
      ...searchParams,
      trashed: getCurrentFolderId() === FOLDER.BIN,
    },
    onSuccess: (data: any) => {
      const { pagination, resources } = data.pages[data.pages.length - 1];

      updateSearchParams({
        ...searchParams,
        pagination,
      });
      setResources(resources);
    },
  });

  const { ref, inView } = useInView();

  useEffect(() => {
    if (inView) {
      fetchNextPage();
    }
  }, [inView]);

  /* const [ref, inView] = useInView();

  useEffect(() => {
    if (inView) {
      console.log(inView);

      fetchNextPage();
    }
  }, [inView]); */

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

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

  const classes = clsx("grid ps-0 list-unstyled");

  return (
    <React.Fragment>
      <animated.ul className={classes}>
        {data?.pages.map((page, index) => (
          // eslint-disable-next-line react/no-array-index-key
          <React.Fragment key={index}>
            {page.resources.map((resource) => {
              const { id, creatorName, name, thumbnail, updatedAt, shared } =
                resource;

              const time = dayjs(updatedAt).locale(currentLanguage).fromNow();

              return (
                <animated.li
                  className="g-col-4"
                  key={id}
                  style={{
                    ...springs,
                  }}
                >
                  <Card
                    app={app}
                    className="c-pointer"
                    creatorName={creatorName}
                    isLoading={isFetching}
                    isPublic={resource.public}
                    isSelected={isResourceSelected(resource)}
                    isShared={resourceIsShared(shared)}
                    messagePublic={i18n("tooltip.public")}
                    messageShared={i18n("tooltip.shared")}
                    name={name}
                    onOpen={() => openResource(resource)}
                    onSelect={() => toggleSelect(resource)}
                    resourceSrc={thumbnail}
                    updatedAt={time}
                    userSrc={session?.avatarUrl}
                  />
                </animated.li>
              );
            })}
          </React.Fragment>
        ))}
      </animated.ul>
      {hasMoreResources && <LoadMore ref={ref} />}
    </React.Fragment>
  );
}
