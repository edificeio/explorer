import { Dispatch, ReactNode } from "react";

import { TreeNode } from "@ode-react-ui/core";
import { OdeProviderParams } from "@shared/types";
import {
  ResourceType,
  IFolder,
  IResource,
  IExplorerContext,
  IExplorerFramework,
  ID,
} from "ode-ts-client";

export interface ExplorerProviderProps {
  children: ReactNode;
  explorerFramework: IExplorerFramework;
  params: OdeProviderParams;
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
  contextRef: React.MutableRefObject<IExplorerContext>;
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
  openResource: () => void;
  openSingleResource: (assetId: ID) => void;
  hideSelectedElement: () => void;
}

export type Action =
  | { type: "GET_RESOURCES"; payload: IResource[] }
  | { type: "CLEAR_RESOURCES" }
  | { type: "GET_FOLDERS"; payload: IFolder[] }
  | { type: "GET_TREEDATA"; payload: TreeNode }
  | { type: "SELECT_FOLDER"; payload: ThingWithAnID }
  | {
      type: "HIDE_SELECTION";
      payload: {
        selectedFolders: string[];
        selectedResources: string[];
      };
    };
