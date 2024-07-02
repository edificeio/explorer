import { type IFolder } from "edifice-ts-client";

import { TreeData } from "@edifice-ui/react";
import TreeNodeFolderWrapper from "./TreeNodeFolderWrapper";
import { arrayUnique } from "./arrayUnique";
import { modifyNode } from "./modifyNode";

export function addNode(
  treeData: TreeData,
  { parentId, newFolder }: { parentId: string; newFolder: IFolder },
): TreeData {
  return modifyNode(treeData, (node) => {
    if (node.id === parentId) {
      const parentAncestors = [
        ...((node as TreeNodeFolderWrapper).folder?.ancestors || []),
      ];
      const ancestors = arrayUnique([...parentAncestors, node.id]);
      const newNode: TreeData = {
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
