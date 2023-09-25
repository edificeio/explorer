import { Suspense, lazy } from "react";

import { LoadingScreen } from "@edifice-ui/react";

import { useSearchContext } from "~/services/queries";
import { useIsRoot, useIsTrash, useHasSelectedNodes } from "~/store";

const EmptyScreenApp = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenApp"),
);
const EmptyScreenNoContentInFolder = lazy(
  async () =>
    await import("~/components/EmptyScreens/EmptyScreenNoContentInFolder"),
);
const EmptyScreenTrash = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenTrash"),
);
const FoldersList = lazy(
  async () =>
    await import("~/features/Explorer/components/FoldersList/FoldersList"),
);
const ResourcesList = lazy(
  async () =>
    await import("~/features/Explorer/components/ResourcesList/ResourcesList"),
);

export const List = () => {
  const isRoot = useIsRoot();
  const isTrashFolder = useIsTrash();
  const hasSelectedNodes = useHasSelectedNodes();
  const { data, isLoading, isFetching, fetchNextPage } = useSearchContext();

  const hasNoFolders = data?.pages[0].folders.length === 0;
  const hasNoResources = data?.pages[0].resources.length === 0;

  const hasNoData = hasNoFolders && hasNoResources;

  return (
    <Suspense fallback={<LoadingScreen />}>
      {!hasNoData && !isLoading && (
        <Suspense fallback={<LoadingScreen />}>
          <FoldersList data={data} isFetching={isFetching} />
          <ResourcesList
            data={data}
            isFetching={isFetching}
            fetchNextPage={fetchNextPage}
          />
        </Suspense>
      )}

      {isRoot && hasNoData && (
        <Suspense fallback={<LoadingScreen />}>
          <EmptyScreenApp />
        </Suspense>
      )}

      {hasSelectedNodes && hasNoData && !isTrashFolder && (
        <Suspense fallback={<LoadingScreen />}>
          <EmptyScreenNoContentInFolder />
        </Suspense>
      )}

      {isTrashFolder && data?.pages[0].resources.length === 0 && (
        <Suspense fallback={<LoadingScreen />}>
          <EmptyScreenTrash />
        </Suspense>
      )}
    </Suspense>
  );
};
