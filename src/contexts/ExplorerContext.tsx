import {
  useMemo,
  createContext,
  useContext,
  ReactNode,
  useEffect,
  useReducer,
} from "react";

import {
  ACTION,
  ID,
  IExplorerContext,
  IExplorerFramework,
  IFolder,
  IResource,
  ResourceType,
} from "ode-ts-client";

import { useOdeContext } from "./OdeContext";

interface ExplorerContextProps {
  explorer: IExplorerFramework;
  context: IExplorerContext;
  onOpen: () => void;
  onCreate: () => void;
  selectFolder: (folder: IFolder) => void;
  deselectFolder: (folder: IFolder) => void;
  deselectAllFolders: () => void;
  selectResource: (res: IResource) => void;
  deselectResource: (res: IResource) => void;
  deselectAllResources: () => void;
  selectedFolders: Array<IFolder>;
  selectedResources: Array<IResource>;
  isFolderSelected: (folder: IFolder) => boolean;
  isResourceSelected: (res: IResource) => boolean;
}

interface ExplorerProviderProps {
  children: ReactNode;
  types: ResourceType[];
}

type ThingWithAnID = IFolder | IResource;
type ActionOnThingsWithAnId = { type: number; thing?: ThingWithAnID };

/** The resources/folders selection reducer */
function selectionReducer<T extends { [id: ID]: ThingWithAnID }>(
  state: T,
  action: ActionOnThingsWithAnId,
) {
  switch (action.type) {
    case 0: {
      // select
      if (action.thing && !Object.hasOwn(state, action.thing.id)) {
        // eslint-disable-next-line no-param-reassign
        (state as any)[action.thing.id] = action.thing;
      }
      return state; // warn: same object
    }
    case 1: {
      // deselect
      if (action.thing && Object.hasOwn(state, action.thing.id)) {
        // eslint-disable-next-line no-param-reassign
        delete state[`${action.thing.id}`];
      }
      return state;
    }
    case 2: {
      // deselect all
      return {};
    }
    default:
      throw Error(`Unknown action ${action.type}`);
  }
}

/** Exploration context is also a React context */
const ExplorerContext = createContext<ExplorerContextProps | null>(null!);

/**
 * This React context is a wrapper for the ode-ts-client explorer framework. It
 * - initiates an exploring context,
 * - memoizes current folder,
 * - memoizes selected resources and folders,
 * - exports functions to select and deselect folders and resources,
 * - ...
 */
export default function ExplorerContextProvider({
  children,
  types,
}: ExplorerProviderProps) {
  const { params, explorer } = useOdeContext();

  // Exploration context
  const context = explorer.createContext(types, params.app);

  // Selected folders and resources
  const [selectedFolders, actionOnFolder] = useReducer(selectionReducer, {});
  const [selectedResources, actionOnResource] = useReducer(
    selectionReducer,
    {},
  );

  function selectFolder(folder: IFolder) {
    actionOnFolder({ type: 0, thing: folder });
  }
  function deselectFolder(folder: IFolder) {
    actionOnFolder({ type: 1, thing: folder });
  }
  function deselectAllFolders() {
    actionOnFolder({ type: 2 });
  }
  function isFolderSelected(folder: IFolder) {
    return Object.hasOwn(selectedFolders, folder.id);
  }

  function selectResource(res: IResource) {
    actionOnResource({ type: 0, thing: res });
  }
  function deselectResource(res: IResource) {
    actionOnResource({ type: 1, thing: res });
  }
  function deselectAllResources() {
    actionOnResource({ type: 2 });
  }
  function isResourceSelected(res: IResource) {
    return Object.hasOwn(selectedResources, res.id);
  }

  function onOpen() {
    return Promise.resolve(Object.values(selectedResources) as Array<IResource>)
      .then((items) => (items.length === 1 ? items[0] : null))
      .then((item) => {
        return !item
          ? Promise.reject(new Error("Cannot open more than 1 resource"))
          : explorer
              .getBus()
              .publish(types[0], ACTION.OPEN, { resourceId: item.assetId });
      });
  }

  function onCreate() {
    return explorer.getBus().publish(types[0], ACTION.CREATE, "test proto");
  }

  const values = useMemo(
    () => ({
      explorer,
      context,
      onOpen,
      onCreate,
      selectFolder,
      deselectFolder,
      deselectAllFolders,
      selectResource,
      deselectResource,
      deselectAllResources,
      selectedFolders: Object.values(selectedFolders) as Array<IFolder>,
      selectedResources: Object.values(selectedResources) as Array<IResource>,
      isFolderSelected,
      isResourceSelected,
    }),
    [],
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

  return (
    <ExplorerContext.Provider value={values}>
      {children}
    </ExplorerContext.Provider>
  );
}

export function useExplorerContext() {
  const context = useContext(ExplorerContext);

  if (!context) {
    throw new Error(`Cannot be used outside of ExplorerContextProvider`);
  }
  return context;
}
