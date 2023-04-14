import { getFolders, searchContext } from "@services/index";
import { useQuery, useInfiniteQuery } from "@tanstack/react-query";

export function useCreateContext({ searchParams, onSuccess }: any) {
  const {
    filters: { folder },
  } = searchParams;

  return useQuery({
    queryKey: ["explorer", folder],
    queryFn: async () => await getFolders({ searchParams }),
    onSuccess: (data) => onSuccess(data),
    // keepPreviousData: true,
  });
}

/* export function useCreateFolder() {
  return useMutation({
    mutationFn: async (newFolder) => await createFolder(newFolder),
  });
} */

/* Remove Folder or Resource */
/* export function useDeleteAll({
  searchParams,
  selectedResources,
  selectedFolders,
}: any) {
  return useMutation({
    mutationFn: async () => {
      const parameters: DeleteParameters = {
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await odeServices.resource(params.app).deleteAll(parameters);
    },
  });
} */

/* export function useSubfolders(folderId: string) {
  return useQuery({
    queryKey: ["folders", folderId],
    queryFn: async () =>
      await odeServices.resource("blog", "blog").listSubfolders(folderId),
    enabled: !!folderId,
  });
} */

export function useInfiniteContext({ searchParams, onSuccess }: any) {
  return useInfiniteQuery({
    queryKey: ["resources"],
    queryFn: async ({ pageParam = 0 }) =>
      await searchContext({
        ...searchParams,
        pagination: {
          ...searchParams.pagination,
          startIdx: pageParam,
        },
      }),
    onSuccess,
    getNextPageParam: (lastPage) =>
      lastPage.pagination.startIdx + lastPage.pagination.pageSize ?? undefined,
  });
}

export function useInvalidateQueries(
  queryClient: {
    removeQueries: (arg0: { queryKey: string[]; exact: boolean }) => void;
  },
  onSuccess: () => void,
) {
  return {
    removeQueries: () => {
      queryClient.removeQueries({
        queryKey: ["resources"],
        exact: true,
      });
      onSuccess();
    },
  };
}
