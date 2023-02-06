/* import { Alert } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks"; */
import useExplorerStore from "@store/index";

interface ModalProps {
  onSuccess?: () => void;
}

export default function useDeleteModal({ onSuccess }: ModalProps) {
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const { getIsTrashSelected, trashSelection, deleteSelection } =
    useExplorerStore((state) => state);
  const isTrashFolder = getIsTrashSelected();

  // ? We could pass hotToast as argument inside deleteSelection or trashSelection ?
  // const { hotToast } = useHotToast(Alert);
  async function onDelete() {
    try {
      if (isTrashFolder) {
        await deleteSelection();
        /* toast.promise(deleteSelection, {
          loading: "Loading",
          success: "Got the data",
          error: "Error when fetching",
        }); */
      } else {
        await trashSelection();
      }
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    isTrashFolder: getIsTrashSelected(),
    onDelete,
  };
}
