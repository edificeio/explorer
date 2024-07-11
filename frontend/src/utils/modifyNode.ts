import { type TreeData } from "@edifice-ui/react";

export function modifyNode(
  data: TreeData,
  callback: (node: TreeData, parent?: TreeData) => TreeData | undefined,
): TreeData {
  // root cannot be undefined
  const root = doModify(data, callback) || data;
  return root;
}

function doModify(
  current: TreeData,
  callback: (node: TreeData, parent?: TreeData) => TreeData | undefined,
  parent?: TreeData,
): TreeData | undefined {
  const result = callback(current, parent);
  if (result?.children?.length) {
    const children: TreeData[] = [];
    for (const child of result?.children || []) {
      const res = doModify(child, callback, result);
      if (res) {
        children.push(res);
      }
    }
    return { ...result!, children };
  }
  return result;
}
