import { useId } from "react";

import { useExplorerContext } from "@contexts/index";
import { FOLDER, IFolder } from "ode-ts-client";
import { SubmitHandler, useForm } from "react-hook-form";

interface FolderFormModalArg {
  edit: boolean;
  onSuccess?: (folder: IFolder) => void;
  onCancel: () => void;
}

interface FolderFormInputs {
  name: string;
}

export default function useFolderFormModal({
  edit,
  onSuccess,
  onCancel,
}: FolderFormModalArg) {
  const {
    getSelectedIFolders,
    getCurrentFolderId,
    updateFolder,
    createFolder,
  } = useExplorerContext();
  const name = edit ? getSelectedIFolders()[0]?.name : undefined;
  const {
    reset,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<FolderFormInputs>({
    mode: "onChange",
    values: { name: name || "" },
  });

  const id = useId();

  const onSubmit: SubmitHandler<FolderFormInputs> = async function ({
    name,
  }: FolderFormInputs) {
    try {
      const parentId = getCurrentFolderId() || FOLDER.DEFAULT;
      if (edit) {
        const folder = getSelectedIFolders()[0];
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

  const formId = `createModal_${id}`;

  return {
    formId,
    errors,
    isSubmitting,
    isDirty,
    isValid,
    register,
    handleSubmit,
    onCancel: () => {
      reset();
      onCancel();
    },
    onSubmit: (name: FolderFormInputs) => {
      onSubmit(name);
    },
  };
}
