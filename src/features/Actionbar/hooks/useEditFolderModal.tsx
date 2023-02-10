import { useId } from "react";

import useExplorerStore from "@store/index";
import { FOLDER, type IFolder } from "ode-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";

interface useEditFolderModalProps {
  edit: boolean;
  onSuccess?: (folder: IFolder) => void;
  onClose: () => void;
}

interface HandlerProps {
  name: string;
}

export default function useEditFolderModal({
  edit,
  onSuccess,
  onClose,
}: useEditFolderModalProps) {
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const getSelectedFolders = useExplorerStore(
    (state) => state.getSelectedFolders,
  );
  const getCurrentFolderId = useExplorerStore(
    (state) => state.getCurrentFolderId,
  );
  const createFolder = useExplorerStore((state) => state.createFolder);
  const updateFolder = useExplorerStore((state) => state.updateFolder);

  const name = edit ? getSelectedFolders()[0]?.name : undefined;
  const {
    reset,
    register,
    handleSubmit,
    setFocus,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<HandlerProps>({
    mode: "onChange",
    values: { name: name || "" },
  });

  const id = useId();

  const onSubmit: SubmitHandler<HandlerProps> = async function ({
    name,
  }: HandlerProps) {
    try {
      const parentId = getCurrentFolderId() || FOLDER.DEFAULT;
      if (edit) {
        const folder = getSelectedFolders()[0];
        const folderId = folder!.id;
        await updateFolder({ id: folderId, parentId, name });
        reset();
        onSuccess?.(folder);
      } else {
        const folder = await createFolder(name, parentId);
        reset();
        onSuccess?.(folder);
      }
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  };

  function onCancel() {
    reset();
    onClose();
  }

  const formId = `createModal_${id}`;

  return {
    formId,
    errors,
    isSubmitting,
    isDirty,
    isValid,
    register,
    setFocus,
    handleSubmit,
    onCancel,
    onSubmit,
  };
}
