import { EmptyScreenApp } from "@features/EmptyScreens/EmptyScreenApp";
import { EmptyScreenNoContentInFolder } from "@features/EmptyScreens/EmptyScreenNoContentInFolder";
import { EmptyScreenTrash } from "@features/EmptyScreens/EmptyScreenTrash";
import { useSearchContext } from "@services/queries";
import { useIsRoot, useIsTrash, useHasSelectedNodes } from "@store/store";

import { FoldersList } from "../FoldersList/FoldersList";
import { ResourcesList } from "../ResourcesList/ResourcesList";

export const List = () => {
  const isRoot = useIsRoot();
  const isTrashFolder = useIsTrash();
  const hasSelectedNodes = useHasSelectedNodes();
  const { data, isLoading } = useSearchContext();

  const hasNoFolders = data?.pages[0].folders.length === 0;
  const hasNoResources = data?.pages[0].resources.length === 0;

  const hasNoData = hasNoFolders && hasNoResources;

  return (
    <>
      {!hasNoData && !isLoading && (
        <>
          <FoldersList />
          <ResourcesList />
        </>
      )}

      {isRoot && hasNoData && <EmptyScreenApp />}

      {hasSelectedNodes && hasNoData && !isTrashFolder && (
        <EmptyScreenNoContentInFolder />
      )}

      {isTrashFolder && data?.pages[0].resources.length === 0 && (
        <EmptyScreenTrash />
      )}
    </>
  );
};
