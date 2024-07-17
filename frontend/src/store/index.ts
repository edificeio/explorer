import {
  findNodeById,
  findParentNode,
  wrapTreeNode,
  type TreeData,
} from "@edifice-ui/react";
import { type QueryClient } from "@tanstack/react-query";
import {
  FOLDER,
  SORT_ORDER,
  type IActionParameters,
  type ID,
  type IFolder,
  type IResource,
  type ISearchParameters,
} from "edifice-ts-client";
import { t } from "i18next";
import { createStore, useStore } from "zustand";

import { AppParams } from "~/config/getExplorerConfig";
import { goToResource, printResource, searchContext } from "~/services/api";

export type ExtractState<S> = S extends {
  getState: () => infer T;
}
  ? T
  : never;

type Params<U> = Parameters<typeof useStore<typeof store, U>>;

interface ElementDrag {
  isDrag: boolean;
  elementDrag: ID | undefined;
}

interface ElementOver {
  isOver: boolean;
  isTreeview: boolean;
  overId: ID | undefined;
}

type State = {
  config: AppParams | undefined;
  searchParams: ISearchParameters & IActionParameters;
  treeData: TreeData;
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
};

type Action = {
  actions: {
    setConfig: (config: AppParams) => void;
    setSearchConfig: (config: { minLength: number }) => void;
    setTreeData: (treeData: TreeData) => void;
    setSearchParams: (
      searchParams: Partial<ISearchParameters & IActionParameters>,
    ) => void;
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
      queryClient,
    }: {
      folderId: ID;
      folder?: IFolder;
      queryClient?: QueryClient;
    }) => void;
    foldTreeItem: (folderId: string) => void;
    selectTreeItem: (folderId: string, queryClient: QueryClient) => void;
    fetchTreeData: (
      folderId: string,
      queryClient: QueryClient,
    ) => Promise<void>;
    gotoPreviousFolder: () => void;
    goToTrash: () => void;
  };
};

const defaultFolder = {
  id: FOLDER.DEFAULT,
  name: t("explorer.filters.mine"),
  section: true,
  children: [],
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
  treeData: defaultFolder,
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

const store = createStore<State & Action>()((set, get) => ({
  ...initialState,
  actions: {
    setConfig: (config) => set({ config }),
    setSearchConfig: (searchConfig: { minLength: number }) =>
      set((state) => ({
        searchConfig: { ...state.searchConfig, ...searchConfig },
      })),
    setTreeData: (treeData: TreeData) =>
      set(() => ({
        treeData,
      })),
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
    openFolder: ({
      folderId,
      folder,
      queryClient,
    }: {
      folderId: ID;
      folder?: IFolder;
      queryClient?: QueryClient;
    }) => {
      const { searchParams } = get();
      const previousId = searchParams.filters.folder as string;
      const selectedNodeId = folderId;

      if (previousId === folderId) return;

      get().actions.fetchTreeData(
        folderId as string,
        queryClient as QueryClient,
      );

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
    fetchTreeData: async (nodeId: string, queryClient: QueryClient) => {
      const folder = findNodeById(get().treeData, nodeId);
      const folderId = folder?.id as string;

      if (Array.isArray(folder?.children) && !!folder.children.length) return;

      const getQueryData = await queryClient.fetchQuery({
        queryKey: [
          "prefetchContext",
          {
            folderId,
            trashed: false,
          },
        ],
        queryFn: async () =>
          await searchContext({
            ...get().searchParams,
            filters: {
              ...get().searchParams.filters,
              folder: folderId,
            },
          }),
      });

      get().actions.setTreeData(
        wrapTreeNode(
          get().treeData,
          getQueryData?.folders,
          folderId || FOLDER.DEFAULT,
        ),
      );
    },
    selectTreeItem: (folderId: string, queryClient: QueryClient) => {
      const { treeData } = get();
      const { openFolder, fetchTreeData } = get().actions;

      const folder = findNodeById(treeData, folderId);

      fetchTreeData(folderId, queryClient);

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
      const { selectedNodeId, treeData, searchParams } = get();
      const { openFolder } = get().actions;

      if (searchParams.search) {
        openFolder({
          folder: defaultFolder as unknown as IFolder,
          folderId: FOLDER.DEFAULT,
        });
      }

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

// React Custom Hook
export function useStoreContext<U>(selector: Params<U>[1]) {
  return useStore(store, selector);
}

// Selectors
const treeData = (state: ExtractState<typeof store>) => state.treeData;
const actionsSelector = (state: ExtractState<typeof store>) => state.actions;

// Getters
export const getTreeData = () => treeData(store.getState());
export const getStoreActions = () => actionsSelector(store.getState());

// Hooks
export const useTreeData = () => useStoreContext(treeData);
export const useStoreActions = () => useStoreContext(actionsSelector);

export const useSearchParams = () =>
  useStoreContext((state) => state.searchParams);

export const useSelectedNodeId = () =>
  useStoreContext((state) => state.selectedNodeId);

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

export const useTreeStatus = () => useStoreContext((state) => state.status);
