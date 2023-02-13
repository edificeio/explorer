import { useEffect } from "react";

import useEditFolderModal from "@features/Actionbar/hooks/useEditFolderModal";
import {
  Modal,
  Button,
  FormControl,
  Label,
  Input,
  useOdeClient,
} from "@ode-react-ui/core";
import { type IFolder } from "ode-ts-client";
import { createPortal } from "react-dom";

interface EditFolderModalProps {
  isOpen: boolean;
  edit: boolean;
  onSuccess?: (folder: IFolder) => void;
  onCancel: () => void;
}

export default function EditFolderModal({
  isOpen,
  edit,
  onSuccess,
  onCancel: onClose,
}: EditFolderModalProps) {
  const { i18n } = useOdeClient();
  const {
    isDirty,
    isValid,
    isSubmitting,
    formId,
    onSubmit,
    onCancel,
    handleSubmit,
    register,
    setFocus,
  } = useEditFolderModal({
    edit,
    onSuccess,
    onClose,
  });

  useEffect(() => {
    if (isOpen) {
      setFocus("name");
    }
  }, [isOpen]);

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id={"modal_" + formId}>
      <Modal.Header onModalClose={onCancel}>
        {i18n(edit ? "explorer.rename.folder" : "explorer.create.folder")}
      </Modal.Header>
      <Modal.Body>
        <form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormControl id="nameFolder" isRequired>
            <Label>{i18n("explorer.create.folder.name")}</Label>
            <Input
              type="text"
              {...register("name", { required: true })}
              placeholder={i18n("explorer.create.folder.name")}
              size="md"
              aria-required={true}
            />
          </FormControl>
        </form>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="tertiary"
          onClick={onCancel}
          type="button"
          variant="ghost"
        >
          {i18n("explorer.cancel")}
        </Button>
        <Button
          form={formId}
          type="submit"
          color="primary"
          variant="filled"
          disabled={!isDirty || !isValid || isSubmitting}
        >
          {i18n(edit ? "explorer.rename" : "explorer.create")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
