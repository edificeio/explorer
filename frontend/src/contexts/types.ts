import { type Dispatch, type ReactNode } from "react";

import { type TreeNode } from "@ode-react-ui/advanced";
import {
  type ResourceType,
  type IFolder,
  type IResource,
  type IExplorerContext,
  type ID,
  type IHttp,
  type ISession,
  type IWebApp,
} from "ode-ts-client";

export interface ExplorerProviderProps {
  children: ReactNode;
}

/** The resources/folders selection reducer */
export type ThingWithAnID = IFolder | IResource;
export interface ActionOnThingsWithAnId {
  type: string;
  payload?: ThingWithAnID;
}

export interface State {
  treeData: TreeNode;
  folders: IFolder[];
  resources: IResource[];
  treeviewStatus: string;
}

export interface ExplorerContextProps {
  contextRef: React.MutableRefObject<IExplorerContext>;
  state: State;
  selectedFolders: IFolder[];
  selectedResources: IResource[];
  i18n: any;
  http: IHttp;
  session: ISession;
  app: IWebApp | undefined;
  appName: string;
  resourceTypes: ResourceType[];
  trashSelected: boolean;
  dispatch: Dispatch<Action>;
  isFolderSelected: (folder: IFolder) => boolean;
  isResourceSelected: (res: IResource) => boolean;
  createResource: () => void;
  handleNextPage: () => void;
  selectFolder: (folder: IFolder) => void;
  selectResource: (res: IResource) => void;
  deselectAllFolders: () => void;
  deselectAllResources: () => void;
  deselectFolder: (folder: IFolder) => void;
  deselectResource: (res: IResource) => void;
  refreshFolder: (params?: {
    addFolder?: IFolder;
    delFolder?: IFolder;
  }) => void;
  printResource: () => void;
  openResource: () => void;
  openSingleResource: (assetId: ID) => void;
  hideSelectedElement: () => void;
}

export type Action =
  | { type: "CLEAR_RESOURCES" }
  | { type: "GET_RESOURCES"; payload: IResource[] }
  | { type: "GET_FOLDERS"; payload: IFolder[] }
  | { type: "GET_TREEDATA"; payload: TreeNode }
  | { type: "GET_TREEVIEW_ACTION"; payload: string }
  | { type: "SELECT_FOLDER"; payload: ThingWithAnID }
  | {
      type: "HIDE_SELECTION";
      payload: {
        selectedFolders: string[];
        selectedResources: string[];
      };
    };
