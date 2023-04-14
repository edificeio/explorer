import { getFolders, searchContext } from "@services/index";
import {
  useQuery,
  useInfiniteQuery,
  type QueryClient,
} from "@tanstack/react-query";

export function useCreateContext({ searchParams, onSuccess }: any) {
  const {
    filters: { folder },
  } = searchParams;

  return useQuery({
    queryKey: ["explorer", folder],
    queryFn: async () => await getFolders({ searchParams }),
    onSuccess: (data) => onSuccess(data),
  });
}

export function useInfiniteContext({ searchParams, onSuccess }: any) {
  return useInfiniteQuery({
    queryKey: [
      "resources",
      {
        folderId: searchParams.filters.folder,
        trashed: searchParams.trashed,
      },
    ],
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
    refetchOnMount: true,
    // keepPreviousData: true,
    // staleTime: Infinity,
  });
}

export function useInvalidateQueries(
  queryClient: QueryClient,
  onSuccess: () => void,
) {
  return {
    removeQueries: () => {
      queryClient.removeQueries({
        queryKey: ["resources"],
        exact: false,
      });
      onSuccess();
    },
  };
}
