import { useExplorerContext } from "@contexts/index";

interface RestoreModalArg {
  onSuccess?: () => void;
}

export default function useRestoreModal({ onSuccess }: RestoreModalArg) {
  const { getIsTrashSelected, trashSelection } = useExplorerContext();
  async function onRestore() {
    try {
      if (getIsTrashSelected()) {
        await trashSelection();
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
    isAlreadyInTrash: getIsTrashSelected(),
    onRestore: () => {
      onRestore();
    },
  };
}
