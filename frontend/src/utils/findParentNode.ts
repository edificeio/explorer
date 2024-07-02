import { TreeData } from "@edifice-ui/react";

export const findParentNode = (
  parentNode: TreeData,
  childId: string,
): TreeData | undefined => {
  if (parentNode.children) {
    for (const child of parentNode.children) {
      if (child.id === childId) {
        return parentNode;
      }
      const foundNode = findParentNode(child, childId);
      if (foundNode) {
        return foundNode;
      }
    }
  }
  return undefined;
};
