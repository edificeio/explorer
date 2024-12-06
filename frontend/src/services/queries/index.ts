import {
  App,
  CreateParameters,
  FOLDER,
  PutShareResponse,
  ResourceType,
  UpdateParameters,
  UpdateResult,
  type IFolder,
  type IResource,
  type ISearchResults,
  type ShareRight,
} from '@edifice.io/client';
import {
  addNode,
  deleteNode,
  moveNode,
  updateNode,
  useEdificeClient,
  useToast,
  useUser,
} from '@edifice.io/react';
import { useShareMutation, useUpdateMutation } from '@edifice.io/react/modals';
import {
  UseMutationOptions,
  UseMutationResult,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query';
import { t } from 'i18next';

import {
  copyResource,
  createFolder,
  createResource,
  deleteAll,
  moveToFolder,
  restoreAll,
  searchContext,
  trashAll,
  updateFolder,
} from '~/services/api';
import {
  useCurrentFolder,
  useFolderIds,
  useResourceAssetIds,
  useResourceIds,
  useResourceWithoutIds,
  useSearchParams,
  useStoreActions,
  useStoreContext,
  useTreeData,
} from '~/store';

export * from './actions';

/**
 * useSearchContext query
 * update state according to currentFolder ID
 * @returns infinite query to load resources
 */
export const useSearchContext = () => {
  const config = useStoreContext((state) => state.config);
  const searchParams = useSearchParams();
  const { filters, trashed, search } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
      search,
    },
  ];

  return useInfiniteQuery<ISearchResults>({
    queryKey,
    queryFn: async ({ pageParam }) => {
      return await searchContext({
        ...searchParams,
        application: config?.app as App,
        types: config?.types as ResourceType[],
        pagination: {
          ...searchParams.pagination,
          startIdx: pageParam as number,
        },
      });
    },
    staleTime: 5000,
    initialPageParam: 0,
    enabled: !!config,
    retry: false,
    getNextPageParam: (lastPage) => {
      return lastPage.pagination.startIdx + lastPage.pagination.pageSize;
    },
  });
};

/**
 * useTrash query
 * Optimistic UI when resource or folder is trashed
 */
export const useTrash = () => {
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const treeData = useTreeData();
  const folderIds = useFolderIds();
  const assetIds = useResourceAssetIds();
  const resourceRealIds = useResourceIds();
  const useAssetIds = useResourceWithoutIds().length > 0;
  const resourceIds = useAssetIds ? assetIds : resourceRealIds;
  const { clearSelectedItems, clearSelectedIds, setTreeData, setSearchParams } =
    useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await trashAll({ searchParams, folderIds, resourceIds, useAssetIds }),
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
    onSuccess: async (data) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        toast.success(t('explorer.trash.title'));

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
                  resources: page.resources.filter((resource: IResource) => {
                    if (useAssetIds) {
                      return !assetIds.includes(resource.assetId);
                    } else {
                      return !resourceIds.includes(resource.id);
                    }
                  }),
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
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const assetIds = useResourceAssetIds();
  const resourceRealIds = useResourceIds();
  const useAssetIds = useResourceWithoutIds().length > 0;
  const resourceIds = useAssetIds ? assetIds : resourceRealIds;
  const {
    setFolderIds,
    setResourceIds,
    setSelectedResources,
    setSelectedFolders,
  } = useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await restoreAll({ searchParams, folderIds, resourceIds, useAssetIds }),
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        toast.success(t('explorer.trash.toast'));

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
                  resources: page.resources.filter((resource: IResource) => {
                    if (useAssetIds) {
                      return !assetIds.includes(resource.assetId);
                    } else {
                      return !resourceIds.includes(resource.id);
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
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const folderIds = useFolderIds();
  const assetIds = useResourceAssetIds();
  const resourceRealIds = useResourceIds();
  const useAssetIds = useResourceWithoutIds().length > 0;
  const resourceIds = useAssetIds ? assetIds : resourceRealIds;
  const { clearSelectedItems, clearSelectedIds } = useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useMutation({
    mutationFn: async () =>
      await deleteAll({ searchParams, folderIds, resourceIds, useAssetIds }),
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
    onSuccess: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);

      if (previousData) {
        toast.success(t('explorer.removed.from.trash'));

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
                  resources: page.resources.filter((resource: IResource) => {
                    if (useAssetIds) {
                      return !assetIds.includes(resource.assetId);
                    } else {
                      return !resourceIds.includes(resource.id);
                    }
                  }),
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

/**
 * useCopyResource query.
 * Optimistic UI when resource is copied.
 */
export const useCopyResource = () => {
  const toast = useToast();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const { user } = useUser();
  const currentFolder = useCurrentFolder();

  const { filters, trashed } = searchParams;
  const TOAST_INFO_ID = 'duplicate_start';

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useMutation({
    mutationFn: async (resource: IResource) => {
      toast.info(t('duplicate.start'), {
        id: TOAST_INFO_ID,
      });
      return await copyResource(searchParams, resource.assetId);
    },
    onSuccess: async (data, variables) => {
      toast.remove(TOAST_INFO_ID);
      toast.success(t('duplicate.done'));

      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);
      const newResource: IResource = {
        ...variables,
        name: `${variables.name}${t('duplicate.suffix')}`,
        assetId: data.duplicateId,
        id: data.duplicateId,
        creatorId: user?.userId as string,
        creatorName: user?.username as string,
        createdAt: Date.now() as unknown as string,
        slug: variables.slug || '',
        modifiedAt: Date.now() as unknown as string,
        modifierId: user?.userId || '',
        modifierName: user?.username || '',
        updatedAt: Date.now() as unknown as string,
        trashed: false,
        rights: [`creator:${user?.userId}`],
      };

      if (previousData) {
        queryClient.setQueryData<InfiniteData<ISearchResults> | undefined>(
          queryKey,
          (prev) => {
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
          },
        );
      }

      // Fix #WB2-1478: Duplicate Backend API creates the duplicated resource in the root folder
      // So in case we are in another folder we need to move the duplicated resource to that folder
      if (currentFolder.id && currentFolder.id !== FOLDER.DEFAULT) {
        moveToFolder({
          searchParams,
          resourceIds: [data.duplicateId],
          folderId: currentFolder.id,
          folderIds: [],
          useAssetIds: true,
        });
      }
    },
    onError: (error) => {
      toast.remove(TOAST_INFO_ID);
      if (typeof error === 'string') {
        toast.error(`${t('duplicate.error')}: ${error}`);
      }
    },
  });
};

export const useMoveItem = () => {
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const treeData = useTreeData();
  const folderIds = useFolderIds();
  const assetIds = useResourceAssetIds();
  const resourceRealIds = useResourceIds();
  const useAssetIds = useResourceWithoutIds().length > 0;
  const resourceIds = useAssetIds ? assetIds : resourceRealIds;

  const { clearSelectedIds, clearSelectedItems, setTreeData, setSearchParams } =
    useStoreActions();

  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useMutation({
    mutationFn: async (folderId: string) =>
      await moveToFolder({
        searchParams,
        folderId,
        folderIds,
        resourceIds,
        useAssetIds,
      }),
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
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
                  resources: page.resources.filter((resource: IResource) => {
                    if (useAssetIds) {
                      return !assetIds.includes(resource.assetId);
                    } else {
                      return !resourceIds.includes(resource.id);
                    }
                  }),
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

      queryClient.invalidateQueries();
    },
    onSettled: () => {
      clearSelectedItems();
      clearSelectedIds();
    },
  });
};

export const useCreateFolder = () => {
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const treeData = useTreeData();
  const { setTreeData } = useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
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
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
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
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const treeData = useTreeData();
  const { setFolderIds, setSelectedFolders, setTreeData } = useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
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
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
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

export const useShareResource = (application: string) => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();

  const { setResourceIds, setSelectedResources } = useStoreActions();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useShareMutation({
    application,
    options: {
      onSuccess: async (
        _data: PutShareResponse,
        variables: { resourceId: string; rights: ShareRight[] },
      ) => {
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
                      if (resource.assetId === variables?.resourceId) {
                        let rights: string[] = [
                          `creator:${resource.creatorId}`,
                        ];

                        if (variables?.rights.length >= 1) {
                          rights = [
                            ...rights,
                            ...variables.rights.flatMap((right) => {
                              return right.actions.map((action) => {
                                return `${right.type}:${right.id}:${action.id}`;
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
    },
  });
};

export const useUpdateResource = (application: string) => {
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { filters, trashed } = searchParams;

  const queryKey = [
    'context',
    {
      folderId: filters.folder,
      filters,
      trashed,
    },
  ];

  return useUpdateMutation({
    application,
    options: {
      onSuccess: async (_data: UpdateResult, variables: UpdateParameters) => {
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
                          thumbnail:
                            typeof thumbnail === 'string'
                              ? thumbnail
                              : URL.createObjectURL(
                                  thumbnail as Blob | MediaSource,
                                ),
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
    },
  });
};

export const useCustomMutation = <
  TData = unknown,
  TError = unknown,
  TVariables = void,
  TContext = unknown,
>(
  options: UseMutationOptions<TData, TError, TVariables, TContext>,
): UseMutationResult<TData, TError, TVariables, TContext> => {
  return useMutation(options);
};

export const useCreateResource = () => {
  const toast = useToast();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const { user } = useUser();
  const { appCode: application } = useEdificeClient();

  const queryKey = [
    'context',
    {
      folderId: searchParams.filters.folder,
      filters: searchParams.filters,
      trashed: searchParams.trashed,
    },
  ];

  return useMutation({
    mutationFn: async (params: CreateParameters) =>
      await createResource({ searchParams, params }),
    onError(error) {
      if (typeof error === 'string') toast.error(t(error));
    },
    onSuccess: async (data, variables) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData<ISearchResults>(queryKey);
      const newResource: IResource = {
        ...variables,
        thumbnail: data.thumbnail || '',
        application,
        assetId: data._id || data.entId || '',
        id: data._id || data.entId || '',
        creatorId: user?.userId as string,
        creatorName: user?.username as string,
        createdAt: Date.now() as unknown as string,
        slug: variables.slug || '',
        modifiedAt: data.modified?.$date || '',
        modifierId: data.author?.userId || '',
        modifierName: data.author?.username || '',
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
