import { lazy, Suspense, useEffect } from "react";

import { LoadingScreen, useOdeClient, useToast } from "@edifice-ui/react";
import { useTranslation } from "react-i18next";

import { useQueryClient } from "@tanstack/react-query";
import { useSearchContext } from "~/services/queries";
import {
  useCurrentFolder,
  useIsRoot,
  useIsTrash,
  useSearchParams,
  useSelectedNodeId,
  useStoreActions,
} from "~/store";

const EmptyScreenApp = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenApp"),
);
const EmptyScreenSearch = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenSearch"),
);
const EmptyScreenError = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenError"),
);
const EmptyScreenNoContentInFolder = lazy(
  async () =>
    await import("~/components/EmptyScreens/EmptyScreenNoContentInFolder"),
);
const EmptyScreenTrash = lazy(
  async () => await import("~/components/EmptyScreens/EmptyScreenTrash"),
);
const FoldersList = lazy(
  async () => await import("~/features/List/FoldersList"),
);
const ResourcesList = lazy(
  async () => await import("~/features/List/ResourcesList"),
);

export const List = () => {
  const isRoot = useIsRoot();
  const isTrashFolder = useIsTrash();
  const selectedNodeId = useSelectedNodeId();
  const searchParams = useSearchParams();
  const currentFolder = useCurrentFolder();
  const toast = useToast();
  const queryClient = useQueryClient();

  const { appCode } = useOdeClient();
  const { t } = useTranslation([appCode]);
  const { setSearchParams, setSearchConfig, fetchTreeData } = useStoreActions();
  const { data, isError, error, isLoading, isFetching, fetchNextPage } =
    useSearchContext();

  const hasNoFolders = data?.pages[0].folders.length === 0;
  const hasNoResources = data?.pages[0].resources.length === 0;
  const hasNoData = hasNoFolders && hasNoResources;

  useEffect(() => {
    if (data) {
      if (data?.pages[0]?.searchConfig) {
        setSearchConfig(data.pages[0].searchConfig);
      }

      if (!searchParams.search && currentFolder.id === "default") {
        fetchTreeData(currentFolder.id as string, queryClient);
      }

      setSearchParams({
        ...searchParams,
        pagination: data?.pages[data?.pages.length - 1]?.pagination,
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  useEffect(() => {
    if (error && typeof error === "string") {
      toast.error(t(error));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [error]);

  if (isLoading) return <LoadingScreen />;

  if (isError) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <EmptyScreenError />
      </Suspense>
    );
  }

  if (searchParams.search && hasNoData) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <EmptyScreenSearch />
      </Suspense>
    );
  }

  if (isRoot && hasNoData) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <EmptyScreenApp />
      </Suspense>
    );
  }

  if (selectedNodeId && hasNoData && !isTrashFolder) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <EmptyScreenNoContentInFolder />
      </Suspense>
    );
  }

  if (isTrashFolder && data?.pages[0].resources.length === 0) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <EmptyScreenTrash />
      </Suspense>
    );
  }

  return (
    <Suspense fallback={<LoadingScreen />}>
      <FoldersList data={data} isFetching={isFetching} />
      <ResourcesList
        data={data}
        isFetching={isFetching}
        fetchNextPage={fetchNextPage}
      />
    </Suspense>
  );
};
