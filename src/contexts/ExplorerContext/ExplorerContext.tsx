import {
  useMemo,
  createContext,
  useContext,
  useEffect,
  useReducer,
  useState,
  Reducer,
} from "react";

import { useOdeContext } from "@contexts/OdeContext/OdeContext";
import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { TreeNode } from "@ode-react-ui/core";
import {
  ACTION,
  ID,
  IExplorerContext,
  IFolder,
  IResource,
} from "ode-ts-client";

import {
  ExplorerContextProps,
  ThingWithAnID,
  ActionOnThingsWithAnId,
  ExplorerProviderProps,
  State,
  Action,
} from "./types";

const Context = createContext<ExplorerContextProps | null>(null!);

const initialState = {
  treeData: {
    id: "default",
    name: "Blogs",
    section: true,
    children: [],
  },
  folders: [],
  resources: [],
};

const reducer: Reducer<State, Action> = (
  state: State = initialState,
  action: Action,
) => {
  switch (action.type) {
    case "HIDE_SELECTION": {
      const { selectedFolders, selectedResources } = action.payload;
      const resources = state.resources.filter(
        (e) => !selectedResources.includes(e.id),
      );
      const folders = state.folders.filter(
        (e) => !selectedFolders.includes(e.id),
      );
      return {
        ...state,
        resources,
        folders,
      };
    }
    case "GET_RESOURCES": {
      const { payload } = action;
      return {
        ...state,
        resources: [...state.resources, ...payload],
      };
    }
    case "CLEAR_RESOURCES": {
      return { ...state, resources: [] };
    }
    case "GET_FOLDERS": {
      const { payload } = action;
      return { ...state, folders: payload };
    }
    case "GET_TREEDATA": {
      const { payload } = action;

      return { ...state, treeData: { ...payload } };
    }
    default:
      return state;
  }
};

/**
 * These actions are used with selectionReducer
 */
const SELECT_ELEMENT = "SELECT_ELEMENT";
const DESELECT_ELEMENT = "DESELECT_ELEMENT";
const DESELECT_ALL = "DESELECT_ALL";

/**
 * This React Reducer is used to:
 * select folder or resource
 * deselect folder or resource
 * deselect all folders or resources.
 */
function selectionReducer<T extends Record<ID, ThingWithAnID>>(
  state: T,
  action: ActionOnThingsWithAnId,
) {
  switch (action.type) {
    case SELECT_ELEMENT: {
      const { payload } = action;
      const id = payload?.id as string;

      /* Add Object in Object with spread syntax and computed value */
      return { ...state, [id]: { ...payload } };
    }
    case DESELECT_ELEMENT: {
      const { payload } = action;
      const id = payload?.id as string;

      /* Remove Object from Object with spread syntax and computed value */
      const { [id]: value, ...rest } = state;
      return { ...rest };
    }
    case DESELECT_ALL: {
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
 * - memoizes Treeview data (treeData) and Ressources list data (listData)
 * - ...
 */
function ExplorerProvider({ children, types }: ExplorerProviderProps) {
  const { params, explorer } = useOdeContext();

  // Exploration context
  // const context = explorer.createContext(types, params.app);
  const [context] = useState<IExplorerContext>(() =>
    explorer.createContext(types, params.app),
  );

  const [state, dispatch] = useReducer(reducer, initialState);

  // Selected folders and resources
  const [selectedFolders, dispatchOnFolder] = useReducer(selectionReducer, {});
  const [selectedResources, dispatchOnResource] = useReducer(
    selectionReducer,
    {},
  );

  useEffect(() => {
    // TODO initialize search parameters. Here and/or in the dedicated React component
    context.getSearchParameters().pagination.pageSize = 2;
    context.getSearchParameters().filters.folder = "default";
    // Do explore...
    context.initialize();
  }, []);

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
        wrapTreeData(resultset?.output?.folders);
        wrapFolderData(resultset?.output?.folders);
        wrapResourceData(resultset?.output?.resources);
      },
      error(err) {
        console.error("something wrong occurred: ", err);
      },
      complete() {
        console.log("done");
      },
    });

    return () => {
      if (subscription) {
        subscription.unsubscribe();
      }
    };
  }, []); // execute effect only once

  function hideSelectedElement() {
    const folderIds = Object.keys(selectedFolders);
    const resourceIds = Object.keys(selectedResources);
    dispatch({
      type: "HIDE_SELECTION",
      payload: { selectedFolders: folderIds, selectedResources: resourceIds },
    });
  }

  function selectFolder(folder: IFolder) {
    dispatchOnFolder({ type: "SELECT_ELEMENT", payload: folder });
  }

  function deselectFolder(folder: IFolder) {
    dispatchOnFolder({ type: "DESELECT_ELEMENT", payload: folder });
  }

  function deselectAllFolders() {
    dispatchOnFolder({ type: "DESELECT_ALL" });
  }

  function isFolderSelected(folder: IFolder) {
    return Object.hasOwn(selectedFolders, folder.id);
  }

  function selectResource(res: IResource) {
    dispatchOnResource({ type: "SELECT_ELEMENT", payload: res });
  }

  function deselectResource(res: IResource) {
    dispatchOnResource({ type: "DESELECT_ELEMENT", payload: res });
  }

  function deselectAllResources() {
    dispatchOnResource({ type: "DESELECT_ALL" });
  }

  async function openResource() {
    const items = Object.values(selectedResources) as IResource[];
    if (items.length !== 1) {
      // TODO display alert
      throw new Error("Cannot open more than 1 resource");
    }
    const item = items[0];
    await explorer
      .getBus()
      .publish(types[0], ACTION.OPEN, { resourceId: item.assetId });
  }

  async function printResource() {
    const items = Object.values(selectedResources) as IResource[];
    if (items.length !== 1) {
      // TODO display alert
      throw new Error("Cannot open more than 1 resource");
    }
    const item = items[0];
    await explorer
      .getBus()
      .publish(types[0], ACTION.PRINT, { resourceId: item.assetId });
  }

  async function refreshFolder() {
    const resultset = await context.getResources();
    wrapFolderData(resultset.folders);
    wrapResourceData(resultset.resources);
  }

  function isResourceSelected(res: IResource) {
    return Object.hasOwn(selectedResources, res.id);
  }

  async function createResource() {
    return await explorer
      .getBus()
      .publish(types[0], ACTION.CREATE, "test proto");
  }

  function findNodeById(id: string, data: TreeNode): TreeNode | undefined {
    let res: TreeNode | undefined;
    if (data?.id === id) {
      return data;
    }
    if (data?.children?.length) {
      data?.children?.every((childNode: any) => {
        res = findNodeById(id, childNode);
        return res === undefined; // break loop if res is found
      });
    }
    return res;
  }

  function wrapTreeData(folders?: IFolder[]) {
    folders?.forEach((folder) => {
      const parentFolder = findNodeById(folder.parentId, state.treeData);
      if (
        !parentFolder?.children?.find((child: any) => child.id === folder.id)
      ) {
        if (parentFolder?.children) {
          parentFolder.children = [
            ...parentFolder.children,
            new TreeNodeFolderWrapper(folder),
          ];
        }
      }
    });

    dispatch({ type: "GET_TREEDATA", payload: state.treeData });
  }

  function wrapResourceData(resources?: IResource[]) {
    if (resources?.length) {
      dispatch({
        type: "GET_RESOURCES",
        payload: resources,
      });
    }
  }

  function wrapFolderData(folders?: IFolder[]) {
    if (folders?.length) {
      dispatch({ type: "GET_FOLDERS", payload: folders });
    }
  }

  function handleNextPage() {
    context.getResources();
  }

  const values = useMemo(
    () => ({
      context,
      explorer,
      state,
      selectedFolders: Object.values(selectedFolders) as IFolder[],
      selectedResources: Object.values(selectedResources) as IResource[],
      dispatch,
      isFolderSelected,
      isResourceSelected,
      handleNextPage,
      openResource,
      createResource,
      deselectAllFolders,
      deselectAllResources,
      deselectFolder,
      deselectResource,
      selectFolder,
      selectResource,
      refreshFolder,
      printResource,
      hideSelectedElement,
    }),
    [selectedFolders, selectedResources, context, state],
  );

  return <Context.Provider value={values}>{children}</Context.Provider>;
}

function useExplorerContext() {
  const context = useContext(Context);

  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
}

export { ExplorerProvider, useExplorerContext };
