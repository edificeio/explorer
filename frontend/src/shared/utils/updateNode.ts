import { type TreeNode } from "@ode-react-ui/components";
import { type IFolder } from "ode-ts-client";

import { modifyNode } from "./modifyNode";
import { TreeNodeFolderWrapper } from "~features/Explorer/adapters";

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
