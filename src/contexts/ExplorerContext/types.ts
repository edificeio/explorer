import { Dispatch, ReactNode } from "react";

import { TreeNode } from "@ode-react-ui/core";
import {
  ResourceType,
  IFolder,
  IResource,
  IExplorerContext,
  IExplorerFramework,
} from "ode-ts-client";

export interface ExplorerProviderProps {
  children: ReactNode;
  types: ResourceType[];
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
}

export interface ExplorerContextProps {
  context: IExplorerContext;
  explorer: IExplorerFramework;
  state: State;
  selectedFolders: IFolder[];
  selectedResources: IResource[];
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
  refreshFolder: () => void;
  printResource: () => void;
}

export type Action =
  | { type: "GET_RESOURCES"; payload: IResource[] }
  | { type: "CLEAR_RESOURCES" }
  | { type: "GET_FOLDERS"; payload: IFolder[] }
  | { type: "GET_TREEDATA"; payload: TreeNode }
  | { type: "SELECT_FOLDER"; payload: ThingWithAnID };
