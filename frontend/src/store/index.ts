import { type TreeNode } from "@ode-react-ui/components";
import { useScrollToTop as scrollToTop } from "@ode-react-ui/hooks";
import { type InfiniteData, type QueryClient } from "@tanstack/react-query";
import {
  FOLDER,
  type ISearchParameters,
  type IFolder,
  type IResource,
  type IActionResult,
  type PublishParameters,
  type ResourceType,
  type ID,
  type IFilter,
  type IOrder,
  type ISearchResults,
} from "ode-ts-client";
import { create } from "zustand";

import {
  createResource,
  goToResource,
  printResource,
  publishResource,
  searchContext,
} from "~/services/api";
import { arrayUnique } from "~/shared/utils/arrayUnique";
import { findNodeById } from "~/shared/utils/findNodeById";
import { getAncestors } from "~/shared/utils/getAncestors";
import { getAppParams } from "~/shared/utils/getAppParams";
import { hasChildren } from "~/shared/utils/hasChildren";
import { wrapTreeNode } from "~/shared/utils/wrapTreeNode";

const { app, types, filters, orders } = getAppParams();

interface State {
  filters: IFilter[];
  orders: IOrder[];
  searchParams: ISearchParameters;
  treeData: TreeNode;
  selectedNodesIds: string[];
  currentFolder: Partial<IFolder> | undefined;
  selectedFolders: IFolder[];
  selectedResources: IResource[];
  folderIds: ID[];
  resourceIds: ID[];
  resourceIsTrash: boolean;
  resourceActionDisable: boolean;
  updaters: {
    setTreeData: (treeData: TreeNode) => void;
    setSearchParams: (searchParams: ISearchParameters) => void;
    setCurrentFolder: (folder: Partial<IFolder>) => void;
    setSelectedFolders: (selectedFolders: IFolder[]) => void;
    setSelectedResources: (selectedResources: IResource[]) => void;
    setFolderIds: (folderIds: ID[]) => void;
    setResourceIds: (resourceIds: ID[]) => void;
    setResourceIsTrash: (resourceIsTrash: boolean) => void;
    setResourceActionDisable: (resourceActionDisable: boolean) => void;
    clearSelectedItems: () => void;
    clearSelectedIds: () => void;
    openResource: (resource: IResource) => void;
    createResource: () => void;
    printSelectedResource: () => void;
    publishApi: (
      type: ResourceType,
      params: PublishParameters,
    ) => Promise<IActionResult | undefined>;
    openFolder: ({
      folderId,
      folder,
    }: {
      folderId: ID;
      folder?: IFolder;
    }) => void;
    foldTreeItem: (folderId: string) => void;
    selectTreeItem: (folderId: string) => void;
    unfoldTreeItem: (
      folderId: string,
      queryClient: QueryClient,
    ) => Promise<void>;
    gotoPreviousFolder: () => void;
    goToTrash: () => void;
  };
}

export const useStoreContext = create<State>()((set, get) => ({
  filters,
  orders,
  searchParams: {
    app,
    types,
    filters: {
      folder: "default",
    },
    orders: { updatedAt: "desc" },
    pagination: {
      startIdx: 0,
      pageSize: 48,
      maxIdx: 0,
    },
    trashed: false,
  },
  treeData: {
    id: FOLDER.DEFAULT,
    name: "Mes blogs",
    section: true,
    children: [],
  },
  selectedNodesIds: ["default"],
  currentFolder: {
    id: "default",
  },
  selectedFolders: [],
  selectedResources: [],
  folderIds: [],
  resourceIds: [],
  resourceIsTrash: false,
  resourceActionDisable: false,
  updaters: {
    setTreeData: (treeData: TreeNode) => set(() => ({ treeData })),
    setSearchParams: (searchParams: ISearchParameters) =>
      set(() => ({ searchParams })),
    setSelectedFolders: (selectedFolders: IFolder[]) =>
      set(() => ({ selectedFolders })),
    setSelectedResources: (selectedResources: IResource[]) =>
      set(() => ({ selectedResources })),
    setFolderIds: (folderIds: ID[]) => set(() => ({ folderIds })),
    setResourceIds: (resourceIds: ID[]) => set(() => ({ resourceIds })),
    setResourceIsTrash: (resourceIsTrash: boolean) =>
      set(() => ({ resourceIsTrash })),
    setResourceActionDisable: (resourceActionDisable: boolean) =>
      set(() => ({ resourceActionDisable })),
    setCurrentFolder: (currentFolder: Partial<IFolder>) =>
      set(() => ({ currentFolder })),
    clearSelectedItems: () =>
      set(() => ({ selectedFolders: [], selectedResources: [] })),
    clearSelectedIds: () => set(() => ({ resourceIds: [], folderIds: [] })),
    openResource: (resource: IResource) => {
      try {
        const { searchParams } = get();
        goToResource({ searchParams, assetId: resource.assetId });
      } catch (error) {
        console.error("explorer open failed: ", error);
      }
    },
    createResource: () => {
      try {
        const { searchParams, currentFolder } = get();
        const folderId = parseInt(currentFolder?.id || "default");
        const safeFolderId = isNaN(folderId) ? undefined : folderId;
        createResource({
          searchParams,
          safeFolderId: safeFolderId as string | undefined,
        });
      } catch (error) {
        console.error("explorer create failed: ", error);
      }
    },
    printSelectedResource: () => {
      try {
        const { searchParams, selectedResources, resourceIds } = get();
        if (selectedResources.length !== 1) {
          throw new Error("Cannot open more than 1 resource");
        }
        const item = selectedResources.find(
          (resource: IResource) => resource.id === resourceIds[0],
        )!;
        printResource({ searchParams, assetId: item.assetId });
      } catch (error) {
        console.error("explorer print failed: ", error);
      }
    },
    publishApi: async (
      _resourceType: ResourceType,
      params: PublishParameters,
    ): Promise<IActionResult | undefined> => {
      const { searchParams } = get();
      const tmp = await publishResource({ searchParams, params });
      return tmp;
    },
    openFolder: ({ folderId, folder }: { folderId: ID; folder?: IFolder }) => {
      const { searchParams, treeData } = get();
      const previousId = searchParams.filters.folder as string;
      const ancestors = getAncestors(folderId, treeData);
      const selectedNodesIds = arrayUnique([...ancestors, folderId]);

      if (previousId === folderId) return;

      set((state) => {
        return {
          ...state,
          // reset selection when changing folder
          folderIds: [],
          resourceIds: [],
          selectedNodesIds,
          currentFolder: folder || {
            id: folderId,
          },
          searchParams: {
            ...searchParams,
            filters: {
              ...searchParams.filters,
              folder: folderId,
            },
            trashed: folderId === FOLDER.BIN,
          },
        };
      });
    },
    foldTreeItem: () => set((state) => ({ ...state, status: "fold" })),
    unfoldTreeItem: async (folderId: string, queryClient: QueryClient) => {
      const { treeData, searchParams } = get();
      set((state) => ({ ...state, status: "unfold" }));
      // fetch subfolders
      if (!hasChildren(folderId, treeData)) {
        await queryClient.prefetchInfiniteQuery({
          queryKey: [
            "prefetchContext",
            {
              folderId,
              trashed: false,
            },
          ],
          queryFn: async () =>
            await searchContext({
              ...searchParams,
              filters: {
                ...searchParams.filters,
                folder: folderId,
              },
            }),
        });
        const data = queryClient.getQueryData<InfiniteData<ISearchResults>>([
          "prefetchContext",
          {
            folderId,
            trashed: false,
          },
        ]);
        set((state) => ({
          ...state,
          treeData: wrapTreeNode(
            treeData,
            data?.pages[0]?.folders,
            folderId || FOLDER.DEFAULT,
          ),
        }));
      }
    },
    selectTreeItem: (folderId: string) => {
      const { treeData } = get();
      const { openFolder } = get().updaters;

      const folder = findNodeById(folderId, treeData);
      const goToTop = scrollToTop();

      goToTop();

      set((state) => ({
        ...state,
        selectedResources: [],
      }));

      openFolder({
        folder: folder as IFolder,
        folderId,
      });
    },
    gotoPreviousFolder: () => {
      const { selectedNodesIds, treeData } = get();
      const { openFolder } = get().updaters;

      const selectedNodesIdsLength = selectedNodesIds.length;
      if (selectedNodesIdsLength < 2) {
        return undefined;
      }
      const previousFolder = findNodeById(
        selectedNodesIds[selectedNodesIdsLength - 2],
        treeData,
      );

      openFolder({
        folder: previousFolder as IFolder,
        folderId: previousFolder?.id || FOLDER.DEFAULT,
      });
    },
    goToTrash: () =>
      set((state) => ({
        ...state,
        selectedNodesIds: [],
        selectedResources: [],
        resourceIds: [],
        folderIds: [],
        searchParams: {
          ...state.searchParams,
          filters: {
            folder: FOLDER.BIN,
          },
          trashed: true,
        },
        currentFolder: {
          id: FOLDER.BIN,
        },
      })),
  },
}));

export const useSearchParams = () =>
  useStoreContext((state) => state.searchParams);

export const useSelectedNodesIds = () =>
  useStoreContext((state) => state.selectedNodesIds);

export const useTreeData = () => useStoreContext((state) => state.treeData);

export const useSelectedFolders = () =>
  useStoreContext((state) => state.selectedFolders);

export const useSelectedResources = () =>
  useStoreContext((state) => state.selectedResources);

export const useFolderIds = () => useStoreContext((state) => state.folderIds);

export const useResourceIds = () =>
  useStoreContext((state) => state.resourceIds);

export const useCurrentFolder = () =>
  useStoreContext((state) => state.currentFolder);

export const useStoreActions = () => useStoreContext((state) => state.updaters);

export const useIsTrash = () => {
  const currentFolder = useCurrentFolder();
  return currentFolder?.id === FOLDER.BIN;
};

export const useResourceIsTrash = () => {
  return useStoreContext((state) => state.resourceIsTrash);
};

export const useResourceActionDisable = () => {
  return useStoreContext((state) => state.resourceActionDisable);
};

export const useIsRoot = () => {
  const currentFolder = useCurrentFolder();
  return currentFolder?.id === "default";
};

export const useHasSelectedNodes = () => {
  const selectedNodesIds = useSelectedNodesIds();
  return selectedNodesIds.length > 1;
};
