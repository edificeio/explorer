import { type TreeNode } from "@ode-react-ui/advanced";

export function hasChildren(folderId: string, data: TreeNode): boolean {
  if (data.id === folderId && data.children) {
    return data.children.length > 0;
  }

  if (data.children) {
    return data.children.some((child: any) => hasChildren(data.id, child));
  }
  return false;
}
