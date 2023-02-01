import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { TreeNode } from "@ode-react-ui/core";
import { deleteNode } from "@shared/utils/deleteNode";
import { moveNode } from "@shared/utils/moveNode";
import { updateNode } from "@shared/utils/updateNode";
import {
  ACTION,
  ConfigurationFrameworkFactory,
  DeleteParameters,
  GetContextResult,
  IBus,
  IFolder,
  ISearchParameters,
  MoveParameters,
  PublishParameters,
  RESOURCE,
  ResourceType,
  TrashParameters,
  UpdateFolderParameters,
  UpdateFolderResult,
} from "ode-ts-client";

import {
  ExplorerStoreProps,
  GetExplorerStateFunction,
  SetExplorerStateFunction,
} from "./types";
import { addNotification } from "./utils";
export const withListActions = ({
  bus,
  toastDelay,
  get,
  set,
}: {
  bus: IBus;
  toastDelay: number;
  get: GetExplorerStateFunction;
  set: SetExplorerStateFunction;
}) => ({
  init: async (props: ExplorerStoreProps) => {
    try {
      // get context from backend
      const { searchParams: searchParamsOld, ready: readyOld } = get();
      if (readyOld) {
        return;
      }
      const searchParams: ISearchParameters = {
        ...searchParamsOld,
        app: props.params.app,
        types: props.types,
      };
      // copy props before
      const ready = !!props.app && !!props.i18n;
      set((state) => ({ ...state, ...props, searchParams, ready }));
      // wait until ready to load
      if (!ready) {
        return;
      }
      const configureFramework = ConfigurationFrameworkFactory.instance();
      const { actions, folders, resources, preferences, orders, filters } =
        (await bus.publish(
          RESOURCE.FOLDER,
          ACTION.INITIALIZE,
          searchParams,
        )) as GetContextResult;
      set(({ treeData, searchParams, ...state }) => ({
        ...state,
        ready: true,
        actions,
        preferences,
        orders,
        filters,
        folderList: folders,
        resourceList: resources,
        searchParams,
        treeData: {
          ...treeData,
          children: folders.map((e) => new TreeNodeFolderWrapper(e)),
          name: configureFramework.Platform.idiom.translate(treeData.name),
        },
      }));
    } catch (e) {
      // if failed push error
      console.error("explorer init failed: ", e);
      addNotification(
        { type: "error", message: "explorer.init.failed" },
        toastDelay,
        set,
      );
    }
  },
  createResource: async () => {
    try {
      const { types } = get();
      await bus.publish(types![0], ACTION.CREATE, "test proto");
    } catch (e) {
      // if failed push error
      console.error("explorer create failed: ", e);
      addNotification(
        { type: "error", message: "explorer.create.failed" },
        toastDelay,
        set,
      );
    }
  },
  openResource: async (assetId: string) => {
    try {
      const { searchParams } = get();
      const res = await bus.publish(searchParams.types[0], ACTION.OPEN, {
        resourceId: assetId,
      });
      return res;
    } catch (e) {
      // if failed push error
      console.error("explorer open failed: ", e);
      addNotification(
        { type: "error", message: "explorer.open.failed" },
        toastDelay,
        set,
      );
    }
  },
  openSelectedResource: async () => {
    try {
      const { searchParams, selectedResources, resourceList } = get();
      if (selectedResources.length > 1) {
        throw new Error("Cannot open more than 1 resource");
      }
      const item = resourceList.find((res) => res.id === selectedResources[0])!;
      await bus.publish(searchParams.types[0], ACTION.OPEN, {
        resourceId: item.assetId,
      });
    } catch (e) {
      // if failed push error
      console.error("explorer open failed: ", e);
      addNotification(
        { type: "error", message: "explorer.open.failed" },
        toastDelay,
        set,
      );
    }
  },
  printSelectedResource: async () => {
    try {
      const { searchParams, selectedResources, resourceList } = get();
      if (selectedResources.length !== 1) {
        throw new Error("Cannot open more than 1 resource");
      }
      const item = resourceList.find((res) => res.id === selectedResources[0])!;
      await bus.publish(searchParams.types[0], ACTION.PRINT, {
        resourceId: item.assetId,
      });
    } catch (e) {
      // if failed push error
      console.error("explorer print failed: ", e);
      addNotification(
        { type: "error", message: "explorer.print.failed" },
        toastDelay,
        set,
      );
    }
  },
  moveSelectedTo: async (destinationId: string) => {
    try {
      const { selectedFolders, selectedResources, searchParams } = get();
      const parameters: MoveParameters = {
        application: searchParams.app,
        folderId: destinationId,
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await bus.publish(RESOURCE.FOLDER, ACTION.MOVE, parameters);
      set(({ ...state }) => {
        const treeData: TreeNode = moveNode(state.treeData, {
          destinationId,
          folders: selectedFolders,
        });
        const resourceList = state.resourceList.filter(
          (e) => !selectedResources.includes(e.id),
        );
        const folderList = state.folderList.filter(
          (e) => !selectedFolders.includes(e.id),
        );
        return { ...state, folderList, resourceList, treeData };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer move failed: ", e);
      addNotification(
        { type: "error", message: "explorer.move.failed" },
        toastDelay,
        set,
      );
    }
  },
  updateFolder: async (folder: {
    id: string;
    name: string;
    parentId: string;
  }) => {
    try {
      const { searchParams } = get();
      const parameters: UpdateFolderParameters = {
        folderId: folder.id,
        name: folder.name,
        parentId: folder.parentId,
        app: searchParams.app,
        type: searchParams.types[0],
        updatedAt: new Date().toISOString(),
      };
      const newFolder = (await bus.publish(
        RESOURCE.FOLDER,
        ACTION.UPD_PROPS,
        parameters,
      )) as UpdateFolderResult;
      set(
        ({ treeData: treeDataOrig, folderList: folderListOrig, ...state }) => {
          // replace folder in tree
          const treeData = updateNode(treeDataOrig, {
            folderId: folder.id,
            newFolder,
          });
          // replace folder in list
          const folderList: IFolder[] = folderListOrig.map((e) => {
            if (e.id === folder.id) {
              return newFolder;
            } else {
              return e;
            }
          });
          return { ...state, treeData, folderList };
        },
      );
    } catch (e) {
      // if failed push error
      console.error("explorer update failed: ", e);
      addNotification(
        { type: "error", message: "explorer.update.failed" },
        toastDelay,
        set,
      );
    }
  },
  deleteSelection: async () => {
    try {
      const { selectedFolders, selectedResources, searchParams } = get();
      const parameters: DeleteParameters = {
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await bus.publish(RESOURCE.FOLDER, ACTION.DELETE, parameters);
      set(({ ...state }) => {
        const treeData: TreeNode = deleteNode(state.treeData, {
          folders: selectedFolders,
        });
        const resourceList = state.resourceList.filter(
          (e) => !selectedResources.includes(e.id),
        );
        const folderList = state.folderList.filter(
          (e) => !selectedFolders.includes(e.id),
        );
        return { ...state, folderList, resourceList, treeData };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer delete failed: ", e);
      addNotification(
        { type: "error", message: "explorer.delete.failed" },
        toastDelay,
        set,
      );
    }
  },
  trash: async ({
    selectedFolders,
    selectedResources,
    trash,
  }: {
    selectedFolders: string[];
    selectedResources: string[];
    trash: boolean;
  }) => {
    try {
      const { searchParams } = get();
      const parameters: TrashParameters = {
        trash,
        application: searchParams.app,
        resourceType: searchParams.types[0],
        resourceIds: selectedResources,
        folderIds: selectedFolders,
      };
      await bus.publish(RESOURCE.FOLDER, ACTION.TRASH, parameters);
      set(({ ...state }) => {
        const treeData: TreeNode = deleteNode(state.treeData, {
          folders: selectedFolders,
        });
        const resourceList = state.resourceList.filter(
          (e) => !selectedResources.includes(e.id),
        );
        const folderList = state.folderList.filter(
          (e) => !selectedFolders.includes(e.id),
        );
        return { ...state, folderList, resourceList, treeData };
      });
    } catch (e) {
      // if failed push error
      console.error("explorer trash failed: ", e);
      addNotification(
        {
          type: "error",
          message: trash ? "explorer.trash.failed" : "explorer.restore.failed",
        },
        toastDelay,
        set,
      );
    }
  },
  trashSelection: async () => {
    const { selectedFolders, selectedResources, trash } = get();
    trash({ selectedFolders, selectedResources, trash: true });
  },
  restoreSelection: async () => {
    const { selectedFolders, selectedResources, trash } = get();
    trash({ selectedFolders, selectedResources, trash: false });
  },
  publish: async (resourceType: ResourceType, params: PublishParameters) => {
    try {
      await bus.publish(resourceType, ACTION.PUBLISH, params);
    } catch (e) {
      // if failed push error
      console.error("explorer publish failed: ", e);
      addNotification(
        {
          type: "error",
          message: "explorer.publish.failed",
        },
        toastDelay,
        set,
      );
    }
  },
});
