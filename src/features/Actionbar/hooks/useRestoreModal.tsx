import useExplorerStore from "@store/index";

interface ModalProps {
  onSuccess?: () => void;
}

export default function useRestoreModal({ onSuccess }: ModalProps) {
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const getIsTrashSelected = useExplorerStore(
    (state) => state.getIsTrashSelected,
  );
  const restoreSelection = useExplorerStore((state) => state.restoreSelection);

  async function onRestore() {
    try {
      if (getIsTrashSelected()) {
        await restoreSelection();
      } else {
        throw new Error("Cannot restore untrashed resources");
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isTrashFolder: getIsTrashSelected(),
    onRestore: () => {
      onRestore();
    },
  };
}
