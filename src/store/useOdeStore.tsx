import { create } from "zustand";

interface State {
  selectedNodesIds: string[];
}

interface Action {
  setSelectedNodesIds: (folderId: string[]) => void;
}

export const useOdeStore = create<State & Action>((set, get) => ({
  selectedNodesIds: ["default"],
  setSelectedNodesIds: (ids: string[]) => {
    set({ selectedNodesIds: ids });
  },
}));

export const useSelectedNodesIds = () =>
  useOdeStore((state) => state.selectedNodesIds);
export const useSetSelectionNodesIds = () =>
  useOdeStore((state) => state.setSelectedNodesIds);
