import { queryOptions, useQuery } from "@tanstack/react-query";
import { IAction } from "edifice-ts-client";
import { AppParams } from "~/config";
import { useStoreContext } from "~/store";
import { sessionHasWorkflowRights } from "../api";

/**
 * actionsQueryOptions: check action availability depending on workflow right
 * @param actions (expects an array of actions)
 * @returns queryOptions with key, fn, and selected data
 */
export const actionsQueryOptions = (
  actions: IAction[],
  config: AppParams | undefined,
) => {
  const actionRights = actions.map((action) => action.workflow);
  /** we remove duplicate workflows */
  const cleanedActions = new Set(actionRights);
  return queryOptions({
    queryKey: [...cleanedActions],
    queryFn: () => sessionHasWorkflowRights([...cleanedActions]),
    select: (data: Record<string, boolean>) => {
      return actions
        .filter((action: IAction) => data[action.workflow])
        .map((action) => ({
          ...action,
          available: true,
        })) as IAction[];
    },
    staleTime: Infinity,
    enabled: !!config,
  });
};

/**
 * useActions query
 * set actions correctly with workflow rights
 * @returns actions data
 */

export const useActions = (actions: IAction[]) => {
  const config = useStoreContext((state) => state.config);
  return useQuery(actionsQueryOptions(actions, config));
};
