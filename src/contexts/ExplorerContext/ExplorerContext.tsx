import {
  createContext,
  Reducer,
  useContext,
  useEffect,
  useReducer,
  useRef,
  useMemo,
} from "react";

import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { TreeNode } from "@ode-react-ui/core";
import {
  ACTION,
  ConfigurationFrameworkFactory,
  FOLDER,
  ID,
  IExplorerContext,
  IFolder,
  IResource,
} from "ode-ts-client";

import {
  Action,
  ActionOnThingsWithAnId,
  ExplorerContextProps,
  ExplorerProviderProps,
  State,
  ThingWithAnID,
} from "./types";

const Context = createContext<ExplorerContextProps | null>(null!);

const initialState = {
  treeData: {
    id: FOLDER.DEFAULT,
    name: "explorer.tree.title",
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
  const configureFramework = ConfigurationFrameworkFactory.instance();
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

      return {
        ...state,
        treeData: {
          ...payload,
          name: configureFramework.Platform.idiom.translate(payload.name),
        },
      };
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

function ExplorerProvider({
  children,
  explorerFramework,
  params,
  types,
  i18n,
  http,
  session,
  app,
}: ExplorerProviderProps) {
  const createContext = explorerFramework.createContext(types, params.app);
  // Exploration context
  const contextRef = useRef<IExplorerContext>(createContext);
  // State
  const [state, dispatch] = useReducer(reducer, initialState);
  // Selected folders and resources
  const [selectedFolders, dispatchOnFolder] = useReducer(selectionReducer, {});
  const [selectedResources, dispatchOnResource] = useReducer(
    selectionReducer,
    {},
  );

  useEffect(() => {
    // TODO initialize search parameters. Here and/or in the dedicated React component
    contextRef.current.getSearchParameters().pagination.pageSize = 4;
    contextRef.current.getSearchParameters().filters.folder = FOLDER.DEFAULT;
    // Do explore...
    (async () => {
      await contextRef.current.initialize();
      // setContext(context);
    })();
  }, []);

  // Observe streamed search results
  useEffect(() => {
    const subscription = contextRef.current.latestResources().subscribe({
      next: (resultset) => {
        // Prepare searching next page
        const { pagination } = contextRef.current.getSearchParameters();
        pagination.maxIdx = resultset.output.pagination.maxIdx;
        pagination.startIdx =
          // eslint-disable-next-line @typescript-eslint/restrict-plus-operands
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

  async function openSingleResource(assetId: ID) {
    return await explorerFramework
      .getBus()
      .publish(types[0], ACTION.OPEN, { resourceId: assetId });
  }

  async function openResource() {
    const items = Object.values(selectedResources) as IResource[];
    if (items.length === 1) {
      // TODO display alert
      throw new Error("Cannot open more than 1 resource");
    }
    const item = items[0];
    await explorerFramework
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
    await explorerFramework
      .getBus()
      .publish(types[0], ACTION.PRINT, { resourceId: item.assetId });
  }

  async function refreshFolder() {
    const resultset = await contextRef.current.getResources();
    wrapFolderData(resultset.folders);
    wrapResourceData(resultset.resources);
  }

  function isResourceSelected(res: IResource) {
    return Object.hasOwn(selectedResources, res.id);
  }

  async function createResource() {
    return await explorerFramework
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

    const updatedTreeData = {
      ...state.treeData,
      name: i18n("explorer.filters.mine"),
    };

    dispatch({ type: "GET_TREEDATA", payload: updatedTreeData });
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
    dispatch({ type: "GET_FOLDERS", payload: folders || [] });
  }

  async function handleNextPage() {
    await contextRef.current.getResources();
  }
  const trashSelected =
    contextRef.current.getSearchParameters().filters.folder === FOLDER.BIN;

  const store = useMemo(
    () => ({
      contextRef,
      state,
      trashSelected,
      selectedFolders: Object.values(selectedFolders) as IFolder[],
      selectedResources: Object.values(selectedResources) as IResource[],
      dispatch,
      isFolderSelected,
      isResourceSelected,
      handleNextPage,
      openResource,
      openSingleResource,
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
      i18n,
      http,
      session,
      app,
      resourceTypes: types,
      appName: params.app,
    }),
    [state, selectedFolders, selectedResources],
  );

  return <Context.Provider value={store}>{children}</Context.Provider>;
}

function useExplorerContext() {
  const context = useContext(Context);

  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
}

export { ExplorerProvider, useExplorerContext };
