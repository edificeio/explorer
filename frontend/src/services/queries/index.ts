/* eslint-disable @typescript-eslint/no-misused-promises */
import { useOdeClient, useUser } from "@edifice-ui/react";
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
  IAction,
  CreateParameters,
} from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { TreeNodeFolderWrapper } from "~/features/Explorer/adapters";
import {
  createFolder,
  createResource,
  deleteAll,
  moveToFolder,
  restoreAll,
  searchContext,
  sessionHasWorkflowRights,
  shareResource,
  trashAll,
  updateFolder,
  updateResource,
} from "~/services/api";
import { addNode } from "~/shared/utils/addNode";
import { deleteNode } from "~/shared/utils/deleteNode";
import { getAppParams } from "~/shared/utils/getAppParams";
import { moveNode } from "~/shared/utils/moveNode";
import { updateNode } from "~/shared/utils/updateNode";
import { wrapTreeNode } from "~/shared/utils/wrapTreeNode";
import {
  useStoreActions,
  useSearchParams,
  useFolderIds,
  useCurrentFolder,
  useTreeData,
  useResourceAssetIds,
} from "~/store";

const { actions, app } = getAppParams();

/**
 * useActions query
 * set actions correctly with workflow rights
 * @returns actions data
 */
export const useActions = () => {
  return useQuery<Record<string, boolean>, Error, IAction[]>({
    queryKey: ["actions"],
    queryFn: async () => {
      const actionRights = actions.map((action) => action.workflow);
      const availableRights = await sessionHasWorkflowRights(actionRights);
      return availableRights;
    },
    select: (data) => {
      return actions.map((action) => ({
        ...action,
        available: data[action.workflow],
      }));
    },
  });
};

/**
 * useSearchContext query
 * update state according to currentFolder ID
 * @returns infinite query to load resources
 */
export const useSearchContext = () => {
  const { appCode } = useOdeClient();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const currentFolder = useCurrentFolder();
  const treeData = useTreeData();
  const { setTreeData, setSearchParams } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
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
          name: t("explorer.filters.mine", { ns: appCode }),
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
    getNextPageParam: (lastPage) =>
      lastPage.pagination.startIdx + lastPage.pagination.pageSize ?? undefined,
  });
};

/**
 * useTrash query
 * Optimistic UI when resource or folder is trashed
 */
export const useTrash = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const treeData = useTreeData();
  const folderIds = useFolderIds();
  const resourceAssetIds = useResourceAssetIds();
  const { clearSelectedItems, clearSelectedIds, setTreeData, setSearchParams } =
    useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await trashAll({ searchParams, folderIds, resourceAssetIds }),
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            const newData = {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  pagination: {
                    ...page.pagination,
                    // @ts-ignore
                    maxIdx: page?.pagination?.maxIdx - data.resources.length,
                  },
                  resources: page.resources.filter(
                    (resource: IResource) =>
                      !resourceAssetIds.includes(resource.assetId),
                  ),
                };
              }),
            };

            const update = deleteNode(treeData, {
              folders: folderIds,
            });

            setTreeData(update);

            setSearchParams({
              ...searchParams,
              pagination: {
                ...searchParams.pagination,
                // @ts-ignore
                maxIdx: searchParams.pagination?.maxIdx - data.resources.length,
              },
            });

            return newData;
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
  const resourceAssetIds = useResourceAssetIds();
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
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await restoreAll({ searchParams, folderIds, resourceAssetIds }),
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
                    (resource: IResource) =>
                      !resourceAssetIds.includes(resource.assetId),
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
 * Optimistic UI when resource is deleted
 */
export const useDelete = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const resourceAssetIds = useResourceAssetIds();
  const { clearSelectedItems, clearSelectedIds } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await deleteAll({ searchParams, folderIds, resourceAssetIds }),
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            const newData = {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  resources: page.resources.filter(
                    (resource: IResource) =>
                      !resourceAssetIds.includes(resource.assetId),
                  ),
                };
              }),
            };

            return newData;
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
  const treeData = useTreeData();
  const folderIds = useFolderIds();
  const resourceAssetIds = useResourceAssetIds();
  const { clearSelectedIds, clearSelectedItems, setTreeData, setSearchParams } =
    useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async (folderId: string) =>
      await moveToFolder({
        searchParams,
        folderId,
        folderIds,
        resourceAssetIds,
      }),
    onSuccess: async (data, variables) => {
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            const update = moveNode(treeData, {
              destinationId: variables,
              folders: folderIds,
            });

            const newData = {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.filter(
                    (folder: IFolder) => !folderIds.includes(folder.id),
                  ),
                  pagination: {
                    ...page.pagination,
                    // @ts-ignore
                    maxIdx: page.pagination?.maxIdx - data.resources.length,
                  },
                  resources: page.resources.filter(
                    (resource: IResource) =>
                      !resourceAssetIds.includes(resource.assetId),
                  ),
                };
              }),
            };

            setTreeData(update);

            setSearchParams({
              ...searchParams,
              pagination: {
                ...searchParams.pagination,
                // @ts-ignore
                maxIdx: searchParams.pagination?.maxIdx - data.resources.length,
              },
            });

            return newData;
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
  const treeData = useTreeData();
  const { setTreeData } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
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
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      const newFolder = {
        ...data,
        parentId: variables.parentId,
        children: [],
        rights: [`creator:${data?.creator_id}`],
      };

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            const newData = {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: [...page.folders, newFolder],
                };
              }),
            };

            const update = addNode(treeData, {
              parentId: variables.parentId,
              newFolder,
            });

            setTreeData(update);

            return newData;
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
  const treeData = useTreeData();
  const { setFolderIds, setSelectedFolders, setTreeData } = useStoreActions();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
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
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        return queryClient.setQueryData<
          InfiniteData<ISearchResults> | undefined
        >(queryKey, (prev) => {
          if (prev) {
            const newData = {
              ...prev,
              pages: prev?.pages.map((page) => {
                return {
                  ...page,
                  folders: page.folders.map((folder: IFolder) => {
                    if (folder.id === data.id) {
                      return {
                        ...data,
                        parentId: variables.parentId,
                        rights: folder.rights,
                      };
                    } else {
                      return folder;
                    }
                  }),
                };
              }),
            };

            const update = updateNode(treeData, {
              folderId: variables.folderId,
              newFolder: data,
            });

            setTreeData(update);

            return newData;
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
      trashed: searchParams.trashed,
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
    onSuccess: async (_data, variables) => {
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
                      let rights: string[] = [`creator:${resource.creatorId}`];

                      if (variables?.shares.length >= 1) {
                        rights = [
                          ...rights,
                          ...variables.shares.flatMap((share) => {
                            return share.actions.map((action) => {
                              return `${share.type}:${share.id}:${action.id}`;
                            });
                          }),
                        ];
                      }

                      return {
                        ...resource,
                        rights,
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
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async (params: UpdateParameters) =>
      await updateResource({ searchParams, params }),
    onSuccess: async (_data, variables) => {
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

export const useCreateResource = () => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { user } = useUser();

  const queryKey = [
    "context",
    {
      folderId: searchParams.filters.folder,
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async (params: CreateParameters) =>
      await createResource({ searchParams, params }),
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      console.log({ data, variables });

      const newResource: IResource = {
        ...variables,
        thumbnail: variables.thumbnail as string,
        application: app,
        assetId: data._id || data.entId || "",
        id: data._id || data.entId || "",
        creatorId: user?.userId as string,
        creatorName: user?.username as string,
        createdAt: Date.now() as unknown as string,
        modifiedAt: data.modified?.$date || "",
        modifierId: data.author?.userId || "",
        modifierName: data.author?.username || "",
        updatedAt: Date.now() as unknown as string,
        trashed: false,
        rights: [`creator:${user?.userId}`],
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
                  resources: [newResource, ...page.resources],
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
