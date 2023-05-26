import { type TreeNode } from "@ode-react-ui/advanced";
import { type IFolder } from "ode-ts-client";

import { modifyNode } from "./modifyNode";
import { TreeNodeFolderWrapper } from "~features/Explorer/adapters";

export const wrapTreeNode = (
  treeNode: TreeNode,
  folders: IFolder[] | undefined,
  parentId: string,
) => {
  // const folderIds = folders.map((e) => e.id);
  return modifyNode(treeNode, (node, parent) => {
    // add missing children if needed
    if (node.id === parentId) {
      node.children = folders?.map((e) => new TreeNodeFolderWrapper(e));
    }
    return node;
  });
};
