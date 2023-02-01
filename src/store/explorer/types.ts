import { TreeNode } from "@ode-react-ui/core";
import { OdeProviderParams } from "@shared/types";
import {
  IAction,
  IActionResult,
  IFilter,
  IFolder,
  IHttp,
  IOrder,
  IPreferences,
  IResource,
  ISearchParameters,
  ISession,
  IWebApp,
  PublishParameters,
  ResourceType,
} from "ode-ts-client";

export interface ExplorerState extends ExplorerStoreProps {
  // global
  ready: boolean;
  actions: IAction[];
  filters: IFilter[];
  orders: IOrder[];
  preferences?: IPreferences;
  searchParams: ISearchParameters;
  notifications: Notification[];
  // list view
  folderList: IFolder[];
  resourceList: IResource[];
  selectedFolders: string[];
  selectedResources: string[];
  // tree view
  treeData: TreeNode;
  treeviewStatus?: TreeViewStatus;
  selectedNodeIds: string[];
}

export interface ExplorerAction {
  // getters
  getPreviousFolder: () => TreeNode | undefined;
  getIsTrashSelected: () => boolean;
  getCurrentFolderId: () => string | undefined;
  getHasNoSelectedNodes: () => boolean;
  getHasSelectedRoot: () => boolean;
  getHasResources: () => boolean;
  getHasResourcesOrFolders: () => boolean;
  getSelectedIResources: () => IResource[];
  getSelectedIFolders: () => IFolder[];
  // list actions
  init: (props: ExplorerStoreProps) => Promise<void>;
  openResource: (assetId: string) => Promise<IActionResult | undefined>;
  openSelectedResource: () => Promise<void>;
  printSelectedResource: () => Promise<void>;
  moveSelectedTo: (destinationId: string) => Promise<void>;
  updateFolder: (props: {
    id: string;
    name: string;
    parentId: string;
  }) => Promise<void>;
  trash: (props: {
    selectedResources: string[];
    selectedFolders: string[];
    trash: boolean;
  }) => Promise<void>;
  trashSelection: () => Promise<void>;
  restoreSelection: () => Promise<void>;
  deleteSelection: () => Promise<void>;
  createResource: () => Promise<void>;
  publish: (type: ResourceType, params: PublishParameters) => Promise<void>;
  // list view
  isFolderSelected: (folder: IFolder) => boolean;
  isResourceSelected: (res: IResource) => boolean;
  getMoreResources: () => Promise<void>;
  reloadListView: () => Promise<void>;
  clearListView: () => void;
  select: (id: string[], type: ElementType) => void;
  deselect: (id: string[], type: ElementType) => void;
  deselectAll: (type: ElementType) => void;
  // tree actions
  createFolder: (name: string, parentId: string) => Promise<IFolder>;
  foldTreeItem: (folderId: string) => void;
  unfoldTreeItem: (folderId: string) => void;
  selectTreeItem: (folderId: string) => void;
  gotoTrash: () => void;
  gotoPreviousFolder: () => void;
  openFolder: (id: string) => Promise<void>;
  loadSubfolders: (folderId: string) => Promise<void>;
}
export type TreeViewStatus = "fold" | "unfold" | "select";
export interface Notification {
  type: "error" | "success";
  message: string;
}

export type ElementType = "folder" | "resource" | "all";

export type SetExplorerStateFunction = (
  partial:
    | (ExplorerState & ExplorerAction)
    | Partial<ExplorerState & ExplorerAction>
    | ((
        state: ExplorerState & ExplorerAction,
      ) => ExplorerState & ExplorerAction),
  replace?: boolean | undefined,
) => void;

export type GetExplorerStateFunction = () => ExplorerState & ExplorerAction;

export interface ExplorerStoreProps {
  params: OdeProviderParams;
  types: ResourceType[];
  i18n: (key: string, params?: Record<string, any> | undefined) => string;
  http: IHttp;
  session: ISession;
  app: IWebApp | undefined;
}

export type ExplorerStore = ExplorerAction & ExplorerState;
