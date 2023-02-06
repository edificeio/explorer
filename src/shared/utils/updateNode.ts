import { TreeNodeFolderWrapper } from "@features/Explorer/adapters";
import { type TreeNode } from "@ode-react-ui/core";
import { type IFolder } from "ode-ts-client";

import { modifyNode } from "./modifyNode";

export function updateNode(
  treeData: TreeNode,
  { folderId, newFolder }: { folderId: string; newFolder: IFolder },
): TreeNode {
  return modifyNode(treeData, (node, parent) => {
    if (node.id === folderId) {
      return new TreeNodeFolderWrapper(newFolder);
    } else {
      return node;
    }
  });
}
