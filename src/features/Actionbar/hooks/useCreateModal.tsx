import { useExplorerContext } from "@contexts/index";
import { FOLDER } from "ode-ts-client";

interface CreateModalArg {
  onSuccess?: () => void;
}

export default function useCreateModal({ onSuccess }: CreateModalArg) {
  const { contextRef, resourceTypes } = useExplorerContext();
  async function onCreate(name: string) {
    try {
      const parentId =
        contextRef.current.getSearchParameters().filters.folder ||
        FOLDER.DEFAULT;
      await contextRef.current.createFolder(resourceTypes[0], parentId, name);
      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  return {
    onCreate: (name: string) => {
      onCreate(name);
    },
  };
}
