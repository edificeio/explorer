import { type TreeData } from "@edifice-ui/react";
import { FOLDER } from "edifice-ts-client";

import { findNodeById } from "./findNodeById";

export function getAncestors(data: TreeData, folderId: string): string[] {
  const findItem = findNodeById(data, folderId);
  if (findItem?.folder?.ancestors) {
    const nodes = findItem?.folder.ancestors || [];
    return [...nodes, folderId];
  } else if (folderId === FOLDER.BIN) {
    return [FOLDER.BIN];
  } else {
    return [FOLDER.DEFAULT];
  }
}
