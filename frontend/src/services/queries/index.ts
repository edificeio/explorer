/* eslint-disable @typescript-eslint/no-misused-promises */
import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import {
  createFolder,
  deleteAll,
  moveToFolder,
  restoreAll,
  searchContext,
  sessionHasWorkflowRights,
  shareResource,
  trashAll,
  updateFolder,
  updateResource,
} from "@services/api/index";
import { translate } from "@shared/constants";
import { getAppParams } from "@shared/utils/getAppParams";
import { wrapTreeNode } from "@shared/utils/wrapTreeNode";
import {
  useStoreActions,
  useSearchParams,
  useFolderIds,
  useResourceIds,
  useCurrentFolder,
  useTreeData,
} from "@store/store";
import {
  useInfiniteQuery,
  type InfiniteData,
  useMutation,
  useQueryClient,
  useQuery,
} from "@tanstack/react-query";
import {
  type ISearchResults,
  type IFolder,
  type IResource,
  type ShareRight,
  type UpdateParameters,
  FOLDER,
} from "ode-ts-client";

const { actions } = getAppParams();

/**
 * useActions query
 * set actions correctly with workflow rights
 * @returns actions data
 */
export const useActions = () => {
  return useQuery({
    queryKey: ["actions"],
    queryFn: async () => {
      const actionRights = actions.map((action) => action.workflow);
      const availableRights = await sessionHasWorkflowRights(actionRights);

      return availableRights;
    },
    select: (data) =>
      actions.map((action) => ({
        ...action,
        available: data[action.workflow],
      })),
  });
};

/**
 * useSearchContext query
 * update state according to currentFolder ID
 * @returns infinite query to load resources
 */
export const useSearchContext = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const currentFolder = useCurrentFolder();
  const treeData = useTreeData();
  const { setTreeData, setSearchParams } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useInfiniteQuery({
    queryKey,
    queryFn: async ({ pageParam = 0 }) =>
      await searchContext({
        ...searchParams,
        pagination: {
          ...searchParams.pagination,
          startIdx: pageParam,
        },
      }),
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey });
      const folders = data?.pages[0]?.folders;

      if (currentFolder?.id === "default") {
        setTreeData({
          id: FOLDER.DEFAULT,
          section: true,
          children: folders.map(
            (folder: IFolder) => new TreeNodeFolderWrapper(folder),
          ),
          name: translate("explorer.filters.mine"),
        });
      } else {
        setTreeData(
          wrapTreeNode(
            treeData,
            folders,
            searchParams.filters.folder || FOLDER.DEFAULT,
          ),
        );
      }

      setSearchParams({
        ...searchParams,
        pagination: data?.pages[data?.pages.length - 1]?.pagination,
      });
    },
    refetchOnMount: false,
    getNextPageParam: (lastPage) =>
      lastPage.pagination.startIdx + lastPage.pagination.pageSize ?? undefined,
  });
};

/**
 * useTrash query
 * Optimistic UI when resource or folder is deleted
 */
export const useTrash = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const resourceIds = useResourceIds();
  const { clearSelectedItems, clearSelectedIds } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await trashAll({ searchParams, folderIds, resourceIds }),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  resources: page.resources.filter(
                    (resource: IResource) => !resourceIds.includes(resource.id),
                  ),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      clearSelectedItems();
      clearSelectedIds();
    },
  });
};

/**
 * useRestore query
 * Optimistic UI when resource is restored
 */
export const useRestore = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const resourceIds = useResourceIds();
  const {
    setFolderIds,
    setResourceIds,
    setSelectedResources,
    setSelectedFolders,
  } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await restoreAll({ searchParams, folderIds, resourceIds }),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  resources: page.resources.filter(
                    (resource: IResource) => !resourceIds.includes(resource.id),
                  ),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      setResourceIds([]);
      setSelectedResources([]);
      setFolderIds([]);
      setSelectedFolders([]);
    },
  });
};

/**
 * useDelete query
 * Optimistic UI when resource is restored
 */
export const useDelete = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const resourceIds = useResourceIds();
  const { clearSelectedItems, clearSelectedIds } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await deleteAll({ searchParams, folderIds, resourceIds }),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  resources: page.resources.filter(
                    (resource: IResource) => !resourceIds.includes(resource.id),
                  ),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      clearSelectedItems();
      clearSelectedIds();
    },
  });
};

export const useMoveItem = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const resourceIds = useResourceIds();
  const { clearSelectedIds, clearSelectedItems } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async (folderId: string) =>
      await moveToFolder({ searchParams, folderId, folderIds, resourceIds }),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  resources: page.resources.filter(
                    (resource: IResource) => !resourceIds.includes(resource.id),
                  ),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      clearSelectedItems();
      clearSelectedIds();
    },
  });
};

export const useCreateFolder = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async ({
      name,
      parentId,
    }: {
      name: string;
      parentId: string;
    }) => await createFolder({ searchParams, name, parentId }),
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      const newFolder = {
        ...data,
        rights: [`creator:${data?.creator_id}`],
      };

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: [...page.folders, newFolder],
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
  });
};

export const useUpdatefolder = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { setFolderIds, setSelectedFolders } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async ({
      folderId,
      name,
      parentId,
    }: {
      folderId: string;
      name: string;
      parentId: string;
    }) => await updateFolder({ searchParams, folderId, parentId, name }),
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.map((folder: IFolder) => {
                    if (folder.id === data.id) {
                      return { ...data, rights: folder.rights };
                    } else {
                      return folder;
                    }
                  }),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      setFolderIds([]);
      setSelectedFolders([]);
    },
  });
};

export const useShareResource = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { setResourceIds, setSelectedResources } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async ({
      entId,
      shares,
    }: {
      entId: string;
      shares: ShareRight[];
    }) => await shareResource({ searchParams, entId, shares }),
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  resources: page.resources.map((resource: IResource) => {
                    if (resource.assetId === variables?.entId) {
                      return {
                        ...resource,
                        shared: variables?.shares.length > 0,
                      };
                    } else {
                      return resource;
                    }
                  }),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
    onSettled: () => {
      setResourceIds([]);
      setSelectedResources([]);
    },
  });
};

export const useUpdateResource = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      isTrashView: searchParams.isTrashView,
    },
  ];

  return useMutation({
    mutationFn: async (params: UpdateParameters) =>
      await updateResource({ searchParams, params }),
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            return {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  resources: page.resources.map((resource: IResource) => {
                    if (resource.assetId === variables?.entId) {
                      const {
                        name,
                        thumbnail,
                        public: pub,
                        description,
                        slug,
                        entId,
                        ...others
                      } = variables;
                      return {
                        ...resource,
                        ...others, // add any custom field
                        name,
                        thumbnail: thumbnail! as string,
                        public: pub,
                        description,
                        slug,
                      };
                    } else {
                      return resource;
                    }
                  }),
                };
              }),
            };
          }
          return undefined;
        });
      }
    },
  });
};
