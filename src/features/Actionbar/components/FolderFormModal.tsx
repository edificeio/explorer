import { useExplorerContext } from "@contexts/index";
import useFolderFormModal from "@features/Actionbar/hooks/useFolderFormModal";
import { Modal, Button, FormControl, Label, Input } from "@ode-react-ui/core";
import { IFolder } from "ode-ts-client";

interface FolderFormModalProps {
  isOpen: boolean;
  edit: boolean;
  onSuccess?: (folder: IFolder) => void;
  onCancel?: () => void;
}

export default function FolderFormModal({
  isOpen,
  edit,
  onSuccess = () => ({}),
  onCancel = () => ({}),
}: FolderFormModalProps) {
  const { i18n } = useExplorerContext();
  const {
    isDirty,
    isValid,
    isSubmitting,
    formId,
    onSubmit,
    handleSubmit,
    register,
  } = useFolderFormModal({
    edit,
    onSuccess,
  });
  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id={"modal_" + formId}>
      <Modal.Header
        onModalClose={() => {
          // TODO fix onModalClose type to avoid this hack
          onCancel();
          return {};
        }}
      >
        {i18n(edit ? "explorer.rename.folder" : "explorer.create.folder")}
      </Modal.Header>
      <Modal.Body>
        <form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormControl id="nameFolder" isRequired>
            <Label>{i18n("explorer.create.folder.name")}</Label>
            <Input
              type="text"
              {...register("name", { required: true })}
              key={`name_${formId}`}
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
    </Modal>
  );
}
