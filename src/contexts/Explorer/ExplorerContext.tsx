import {
  useMemo,
  createContext,
  useContext,
  useEffect,
  useReducer,
} from "react";

import { ACTION, ID, IFolder, IResource } from "ode-ts-client";

import { useOdeContext } from "../Ode/OdeContext";
import {
  ExplorerContextProps,
  ThingWithAnID,
  ActionOnThingsWithAnId,
  ExplorerProviderProps,
} from "./types";

const Context = createContext<ExplorerContextProps | null>(null!);

function selectionReducer<T extends Record<ID, ThingWithAnID>>(
  state: T,
  action: ActionOnThingsWithAnId,
) {
  switch (action.type) {
    case 0: {
      const { payload } = action;
      const id = payload?.id as string;

      /* Add Object in Object with spread syntax and computed value */
      return { ...state, [id]: { ...payload } };
    }
    case 1: {
      const { payload } = action;
      const id = payload?.id as string;

      /* Remove Object from Object with spread syntax and computed value */
      const { [id]: value, ...rest } = state;
      return { ...rest };
    }
    case 2: {
      /* Reset state with empty Object */
      return {};
    }
    default:
      throw Error(`Unknown action ${action.type}`);
  }
}

/**
 * This React context is a wrapper for the ode-ts-client explorer framework. It
 * - initiates an exploring context,
 * - memoizes current folder,
 * - memoizes selected resources and folders,
 * - exports functions to select and deselect folders and resources,
 * - ...
 */
export default function ExplorerProvider({
  children,
  types,
}: ExplorerProviderProps) {
  const { params, explorer } = useOdeContext();

  // Exploration context
  const context = explorer.createContext(types, params.app);

  // Selected folders and resources
  const [selectedFolders, dispatchOnFolder] = useReducer(selectionReducer, {});
  const [selectedResources, dispatchOnResource] = useReducer(
    selectionReducer,
    {},
  );

  function selectFolder(folder: IFolder) {
    dispatchOnFolder({ type: 0, payload: folder });
  }

  function deselectFolder(folder: IFolder) {
    dispatchOnFolder({ type: 1, payload: folder });
  }

  function deselectAllFolders() {
    dispatchOnFolder({ type: 2 });
  }

  function isFolderSelected(folder: IFolder) {
    return Object.hasOwn(selectedFolders, folder.id);
  }

  function selectResource(res: IResource) {
    dispatchOnResource({ type: 0, payload: res });
  }

  function deselectResource(res: IResource) {
    dispatchOnResource({ type: 1, payload: res });
  }

  function deselectAllResources() {
    dispatchOnResource({ type: 2 });
  }

  function isResourceSelected(res: IResource) {
    return Object.hasOwn(selectedResources, res.id);
  }

  async function openResource() {
    return await Promise.resolve(
      Object.values(selectedResources) as IResource[],
    )
      .then((items) => (items.length === 1 ? items[0] : null))
      .then(async (item) => {
        return !item
          ? await Promise.reject(new Error("Cannot open more than 1 resource"))
          : await explorer
              .getBus()
              .publish(types[0], ACTION.OPEN, { resourceId: item.assetId });
      });
  }

  async function createResource() {
    return await explorer
      .getBus()
      .publish(types[0], ACTION.CREATE, "test proto");
  }

  const values = useMemo(
    () => ({
      context,
      explorer,
      selectedFolders: Object.values(selectedFolders) as IFolder[],
      selectedResources: Object.values(selectedResources) as IResource[],
      isFolderSelected,
      isResourceSelected,
      openResource,
      createResource,
      deselectAllFolders,
      deselectAllResources,
      deselectFolder,
      deselectResource,
      selectFolder,
      selectResource,
    }),
    [selectedFolders, selectedResources],
  );

  // Observe streamed search results
  useEffect(() => {
    const subscription = context.latestResources().subscribe({
      next: (resultset) => {
        // Prepare searching next page
        const { pagination } = context.getSearchParameters();
        pagination.maxIdx = resultset.output.pagination.maxIdx;
        pagination.startIdx =
          resultset.output.pagination.startIdx +
          resultset.output.pagination.pageSize;
        if (
          typeof pagination.maxIdx !== "undefined" &&
          pagination.startIdx > pagination.maxIdx
        ) {
          pagination.startIdx = pagination.maxIdx;
        }
      },
    });

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  }, []); // execute effect only once

  return <Context.Provider value={values}>{children}</Context.Provider>;
}

export function useExplorerContext() {
  const context = useContext(Context);

  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
}
