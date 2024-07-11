import { type TreeData } from "@edifice-ui/react";

export function findNodeById(data: TreeData, id: string): TreeData | undefined {
  let res: TreeData | undefined;
  if (data?.id === id) {
    return data;
  }
  if (data?.children?.length) {
    data?.children?.every((childNode: TreeData) => {
      res = findNodeById(childNode, id);
      return res === undefined; // break loop if res is found
    });
  }
  return res;
}
