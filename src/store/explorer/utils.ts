import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { TreeNode } from "@ode-react-ui/core";
import { modifyNode } from "@shared/utils/modifyNode";
import { IFolder } from "ode-ts-client";

import { SetExplorerStateFunction, Notification } from "./types";

export const wrapTreeNode = (
  treeNode: TreeNode,
  folders: IFolder[],
  parentId: string,
) => {
  // const folderIds = folders.map((e) => e.id);
  return modifyNode(treeNode, (node, parent) => {
    // add missing children if needed
    if (node.id === parentId) {
      node.children = folders.map((e) => new TreeNodeFolderWrapper(e));
    }
    // modify existing node
    /* if (folderIds.includes(node.id)) {
      const folder = folders.find((e) => e.id === node.id)!;
      return new TreeNodeFolderWrapper(folder);
    } else {
    }
      */
    return node;
  });
};

export const addNotification = (
  notif: Notification,
  timer: number,
  set: SetExplorerStateFunction,
) => {
  set(({ notifications, ...state }) => {
    return { ...state, notifications: [...notifications, notif] };
  });
  setTimeout(() => {
    set((state) => {
      const notifications = state.notifications.filter((n) => {
        return n !== notif;
      });
      return { ...state, notifications };
    });
  }, timer);
};
