import { type TreeData } from "@edifice-ui/react";

export function hasChildren(folderId: string, data: TreeData): boolean {
  if (data.id === folderId && data.children) {
    return data.children.length > 0;
  }

  if (data.children) {
    return data.children.some((child: TreeData) => hasChildren(data.id, child));
  }
  return false;
}
