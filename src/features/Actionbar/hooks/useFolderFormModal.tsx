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
  const { contextRef, resourceTypes, selectedFolders } = useExplorerContext();
  const name = edit ? selectedFolders[0]?.name : undefined;
  const {
    reset,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<FolderFormInputs>({
    mode: "onChange",
    values: { name: name || "" },
  });

  const onSubmit: SubmitHandler<FolderFormInputs> = async function ({
    name,
  }: FolderFormInputs) {
    try {
      const parentId =
        contextRef.current.getSearchParameters().filters.folder ||
        FOLDER.DEFAULT;
      if (edit) {
        const folderId = selectedFolders[0].id;
        await contextRef.current.updateFolder(
          folderId,
          resourceTypes[0],
          parentId,
          name,
        );
        selectedFolders[0].name = name;
        reset();
        onSuccess?.(selectedFolders[0]);
      } else {
        const folder = await contextRef.current.createFolder(
          resourceTypes[0],
          parentId,
          name,
        );
        reset();
        onSuccess?.(folder);
      }
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  };

  const formId = `createModal_${new Date().getTime()}`;

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
