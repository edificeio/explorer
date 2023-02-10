import { ExplorerFrameworkFactory } from "ode-ts-client";
import { create } from "zustand";

import { createExplorerSlice, type ExplorerSlice } from "./explorerSlice";
import { createFolderSlice, type FolderSlice } from "./folderSlice";
import { createResourceSlice, type ResourceSlice } from "./resourceSlice";
import { createTrashSlice, type TrashSlice } from "./trashSlice";
import { createTreeviewSlice, type TreeviewSlice } from "./treeviewSlice";

/**
 * * Combined every slice for the combine store
 */
export type State = ExplorerSlice &
  FolderSlice &
  ResourceSlice &
  TrashSlice &
  TreeviewSlice;

export const BUS = ExplorerFrameworkFactory.instance().getBus();

/**
 * ! use unique store if states don't communicate together
 * ! use slices and a bind store if states communicate together
 * E.g Unique Store https://gist.github.com/ccreusat/29b949e8abae1b9dc752a4e894be7fab
 * E.g Slice https://gist.github.com/ccreusat/5ce0ed1314273dec2423299cce16a162
 */

// * https://docs.pmnd.rs/zustand/guides/slices-pattern
const useExplorerStore = create<State>()((...props) => ({
  ...createExplorerSlice(...props),
  ...createFolderSlice(...props),
  ...createResourceSlice(...props),
  ...createTrashSlice(...props),
  ...createTreeviewSlice(...props),
}));

export default useExplorerStore;
