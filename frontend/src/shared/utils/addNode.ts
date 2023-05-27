import { type TreeNode } from "@ode-react-ui/components";
import { type IFolder } from "ode-ts-client";

import { arrayUnique } from "./arrayUnique";
import { modifyNode } from "./modifyNode";
import { TreeNodeFolderWrapper } from "~features/Explorer/adapters";

export function addNode(
  treeData: TreeNode,
  { parentId, newFolder }: { parentId: string; newFolder: IFolder },
): TreeNode {
  return modifyNode(treeData, (node, parent) => {
    if (node.id === parentId) {
      const parentAncestors = [
        ...((node as TreeNodeFolderWrapper).folder?.ancestors || []),
      ];
      const ancestors = arrayUnique([...parentAncestors, node.id]);
      const newNode: TreeNode = {
        ...node,
        children: [
          ...(node.children || []),
          new TreeNodeFolderWrapper({ ...newFolder, ancestors }),
        ],
      };
      return newNode;
    } else {
      return node;
    }
  });
}
