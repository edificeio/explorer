import {
  useScrollToTop as scrollToTop,
  type TreeNode,
} from "@edifice-ui/react";
import { type InfiniteData, type QueryClient } from "@tanstack/react-query";
import {
  FOLDER,
  SORT_ORDER,
  type IActionParameters,
  type ID,
  type IFolder,
  type IResource,
  type ISearchParameters,
  type ISearchResults,
} from "edifice-ts-client";
import { t } from "i18next";
import { create } from "zustand";

import { AppParams } from "~/config/getExplorerConfig";
import { goToResource, printResource, searchContext } from "~/services/api";
import { findNodeById } from "~/utils/findNodeById";
import { findParentNode } from "~/utils/findParentNode";
import { hasChildren } from "~/utils/hasChildren";
import { wrapTreeNode } from "~/utils/wrapTreeNode";

interface ElementDrag {
  isDrag: boolean;
  elementDrag: ID | undefined;
}

interface ElementOver {
  isOver: boolean;
  isTreeview: boolean;
  overId: ID | undefined;
}

interface State {
  config: AppParams | undefined;
  searchParams: ISearchParameters & IActionParameters;
  treeData: TreeNode;
  selectedNodeId: string | undefined;
  currentFolder: Partial<IFolder>;
  selectedFolders: IFolder[];
  selectedResources: IResource[];
  folderIds: ID[];
  resourceIds: ID[];
  resourceIsTrash: boolean;
  resourceOrFolderIsDraggable: ElementDrag;
  elementDragOver: ElementOver;
  resourceActionDisable: boolean;
  searchConfig: { minLength: number };
  status: string | undefined;
}

type Action = {
  updaters: {
    setConfig: (config: AppParams) => void;
    setSearchConfig: (config: { minLength: number }) => void;
    setTreeData: (treeData: TreeNode) => void;
    setSearchParams: (
      searchParams: Partial<ISearchParameters & IActionParameters>,
    ) => void;
    setCurrentFolder: (folder: Partial<IFolder>) => void;
    setSelectedFolders: (selectedFolders: IFolder[]) => void;
    setSelectedResources: (selectedResources: IResource[]) => void;
    setFolderIds: (folderIds: ID[]) => void;
    setResourceIds: (resourceIds: ID[]) => void;
    setResourceIsTrash: (resourceIsTrash: boolean) => void;
    setResourceOrFolderIsDraggable: (
      resourceOrFolderIsDraggable: ElementDrag,
    ) => void;
    setElementDragOver: (elementDragOver: ElementOver) => void;
    setResourceActionDisable: (resourceActionDisable: boolean) => void;
    clearSelectedItems: () => void;
    clearSelectedIds: () => void;
    openResource: (resource: IResource) => void;
    printSelectedResource: () => void;
    openFolder: ({
      folderId,
      folder,
    }: {
      folderId: ID;
      folder?: IFolder;
    }) => void;
    foldTreeItem: (folderId: string) => void;
    selectTreeItem: (folderId: string) => void;
    overTreeItem: (folderId: string, queryClient: QueryClient) => Promise<void>;
    unfoldTreeItem: (
      folderId: string,
      queryClient: QueryClient,
    ) => Promise<void>;
    gotoPreviousFolder: () => void;
    goToTrash: () => void;
  };
};

const initialState = {
  config: undefined,
  searchConfig: { minLength: 1 },
  searchParams: {
    filters: {
      folder: FOLDER.DEFAULT,
      owner: undefined,
      shared: undefined,
      public: undefined,
    },
    orders: { updatedAt: SORT_ORDER.DESC },
    application: "",
    types: [],
    pagination: {
      startIdx: 0,
      pageSize: 48,
      maxIdx: 0,
    },
    trashed: false,
  },
  treeData: {
    id: FOLDER.DEFAULT,
    name: t("explorer.filters.mine"),
    section: true,
    children: [],
  },
  selectedNodeId: "default",
  currentFolder: {
    id: "default",
  },
  selectedFolders: [],
  selectedResources: [],
  folderIds: [],
  resourceIds: [],
  resourceIsTrash: false,
  resourceOrFolderIsDraggable: {
    isDrag: false,
    elementDrag: undefined,
  },
  elementDragOver: {
    isOver: false,
    isTreeview: false,
    overId: undefined,
  },
  resourceActionDisable: false,
  status: undefined,
};

export const useStoreContext = create<State & Action>()((set, get) => ({
  ...initialState,
  updaters: {
    setConfig: (config) => set({ config }),
    setSearchConfig: (searchConfig: { minLength: number }) =>
      set((state) => ({
        searchConfig: { ...state.searchConfig, ...searchConfig },
      })),
    setTreeData: (treeData: TreeNode) => set(() => ({ treeData })),
    setSearchParams: (searchParams: Partial<ISearchParameters>) => {
      set((state) => {
        const { searchParams: previousSearchParams } = state;
        if (previousSearchParams.search !== searchParams.search) {
          if (searchParams.search) {
            // reset selection and folder if we are searching
            return {
              ...state,
              selectedFolders: [],
              selectedNodeId: undefined,
              selectedResources: [],
              currentFolder: undefined,
              searchParams: {
                ...previousSearchParams,
                ...searchParams,
                trashed: false,
                filters: {
                  ...previousSearchParams.filters,
                  folder: undefined,
                },
              },
            };
          } else {
            // reset selection if we are not searching
            return {
              ...state,
              selectedFolders: [],
              selectedNodeId: "default",
              selectedResources: [],
              currentFolder: {
                id: "default",
              },
              searchParams: {
                ...previousSearchParams,
                ...searchParams,
                trashed: false,
                filters: {
                  ...previousSearchParams.filters,
                },
              },
            };
          }
        } else {
          return {
            searchParams: { ...previousSearchParams, ...searchParams },
          };
        }
      });
    },
    setSelectedFolders: (selectedFolders: IFolder[]) =>
      set(() => ({ selectedFolders })),
    setSelectedResources: (selectedResources: IResource[]) =>
      set(() => ({ selectedResources })),
    setFolderIds: (folderIds: ID[]) => set(() => ({ folderIds })),
    setResourceIds: (resourceIds: ID[]) => set(() => ({ resourceIds })),
    setResourceIsTrash: (resourceIsTrash: boolean) =>
      set(() => ({ resourceIsTrash })),
    setResourceOrFolderIsDraggable: (
      resourceOrFolderIsDraggable: ElementDrag,
    ) => set(() => ({ resourceOrFolderIsDraggable })),
    setElementDragOver: (elementDragOver: ElementOver) =>
      set(() => ({ elementDragOver })),
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
    openFolder: ({ folderId, folder }: { folderId: ID; folder?: IFolder }) => {
      const { searchParams } = get();
      const previousId = searchParams.filters.folder as string;
      const selectedNodeId = folderId;

      if (previousId === folderId) return;

      set((state) => {
        return {
          ...state,
          // reset selection when changing folder
          folderIds: [],
          resourceIds: [],
          selectedNodeId,
          currentFolder: folder || {
            id: folderId,
          },
          searchParams: {
            ...searchParams,
            search: undefined,
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
          initialPageParam: 0,
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
            state.treeData,
            data?.pages[0]?.folders,
            folderId || FOLDER.DEFAULT,
          ),
        }));
      }
    },
    overTreeItem: async (folderId: string, queryClient: QueryClient) => {
      const { unfoldTreeItem } = get().updaters;
      unfoldTreeItem(folderId, queryClient);
    },
    selectTreeItem: (folderId: string) => {
      const { treeData } = get();
      const { openFolder } = get().updaters;

      const folder = findNodeById(treeData, folderId);
      scrollToTop();

      set((state) => ({
        ...state,
        searchParams: {
          ...state.searchParams,
          search: undefined,
        },
        status: "select",
        selectedResources: [],
      }));

      openFolder({
        folder: folder as IFolder,
        folderId,
      });
    },
    gotoPreviousFolder: () => {
      const { selectedNodeId, treeData } = get();
      const { openFolder } = get().updaters;

      if (!selectedNodeId) return;

      const previousFolder = findParentNode(treeData, selectedNodeId);

      openFolder({
        folder: previousFolder as IFolder,
        folderId: previousFolder?.id || FOLDER.DEFAULT,
      });
    },
    goToTrash: () => {
      set((state) => ({
        ...state,
        selectedNodeId: undefined,
        selectedResources: [],
        resourceIds: [],
        folderIds: [],
        status: "select",
        searchParams: {
          ...state.searchParams,
          search: undefined,
          filters: {
            folder: FOLDER.BIN,
          },
          trashed: true,
        },
        currentFolder: {
          id: FOLDER.BIN,
        },
      }));
    },
  },
}));

export const useSearchParams = () =>
  useStoreContext((state) => state.searchParams);

export const useSelectedNodeId = () =>
  useStoreContext((state) => state.selectedNodeId);

export const useTreeData = () => useStoreContext((state) => state.treeData);

export const useSelectedFolders = () =>
  useStoreContext((state) => state.selectedFolders);

export const useSelectedResources = () =>
  useStoreContext((state) => state.selectedResources);

export const useSearchConfig = () =>
  useStoreContext((state) => state.searchConfig);

export const useFolderIds = () => useStoreContext((state) => state.folderIds);

export const useResourceIds = () =>
  useStoreContext((state) => state.resourceIds);

export const useResourceAssetIds = () =>
  useStoreContext((state) => state.selectedResources.map((r) => r.assetId));

export const useResourceWithoutIds = () =>
  useStoreContext((state) =>
    state.selectedResources.filter((r) => r.assetId === r.id),
  );

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

export const useResourceOrFolderIsDraggable = () => {
  return useStoreContext((state) => state.resourceOrFolderIsDraggable);
};

export const useElementDragOver = () => {
  return useStoreContext((state) => state.elementDragOver);
};

export const useResourceActionDisable = () => {
  return useStoreContext((state) => state.resourceActionDisable);
};

export const useIsRoot = () => {
  const currentFolder = useCurrentFolder();
  return currentFolder?.id === "default";
};

/* export const useHasSelectedNodes = () => {
  const selectedNodesIds = useSEl();
  return selectedNodesIds.length > 1;
}; */

export const useTreeStatus = () => useStoreContext((state) => state.status);
