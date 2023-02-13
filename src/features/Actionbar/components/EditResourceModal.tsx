import {
  Alert,
  Button,
  FormControl,
  Heading,
  ImagePicker,
  Input,
  Label,
  Modal,
  useOdeClient,
} from "@ode-react-ui/core";
import { Copy } from "@ode-react-ui/icons";
import useExplorerStore from "@store/index";
import { createPortal } from "react-dom";

import useEditResourceModal from "../hooks/useEditResourceModal";

interface EditResourceModalProps {
  isOpen: boolean;
  edit: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function EditResourceModal({
  isOpen,
  edit,
  onSuccess,
  onCancel,
}: EditResourceModalProps) {
  const { i18n } = useOdeClient();
  const getSelectedIResources = useExplorerStore(
    (state) => state.getSelectedIResources,
  );
  const {
    formId,
    isValid,
    isSubmitting,
    register,
    handleSubmit,
    onSubmit,
    handleUploadImage,
  } = useEditResourceModal({
    edit,
    onSuccess,
    onCancel,
  });

  return createPortal(
    <Modal
      id="resource_edit_modal"
      size="lg"
      isOpen={isOpen}
      onModalClose={onCancel}
    >
      <Modal.Header onModalClose={onCancel}>
        {i18n(
          edit
            ? "explorer.resource.editModal.header.edit"
            : "explorer.resource.editModal.header.create",
        )}
      </Modal.Header>

      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {i18n("explorer.resource.editModal.heading.general")}
        </Heading>

        <form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <div className="d-flex flex-column flex-md-row gap-16 mb-24">
            <ImagePicker
              src={getSelectedIResources()[0]?.thumbnail}
              label={i18n("explorer.imagepicker.label")}
              addButtonLabel={i18n("explorer.imagepicker.button.add")}
              deleteButtonLabel={i18n("explorer.imagepicker.button.delete")}
              onUploadImage={handleUploadImage}
              onDeleteImage={() => {}}
              className="align-self-center"
            />

            <div className="col">
              <FormControl id="title" className="mb-16" isRequired>
                <Label>{i18n("title")}</Label>
                <Input
                  type="text"
                  defaultValue={edit ? getSelectedIResources()[0]?.name : ""}
                  {...register("title", { required: true })}
                  placeholder={i18n(
                    "explorer.resource.editModal.title.placeholder",
                  )}
                  size="md"
                  aria-required={true}
                />
              </FormControl>
              <FormControl id="description" isOptional>
                <Label>{i18n("description")}</Label>
                <Input
                  type="text"
                  defaultValue={
                    edit ? getSelectedIResources()[0]?.description : ""
                  }
                  {...register("description")}
                  placeholder={i18n(
                    "explorer.resource.editModal.description.placeholder",
                  )}
                  size="md"
                />
              </FormControl>
            </div>
          </div>

          <Heading headingStyle="h4" level="h3" className="mb-16">
            {i18n("explorer.resource.editModal.heading.access")}
          </Heading>

          <Alert type="info">
            {i18n("explorer.resource.editModal.access.alert")}
          </Alert>

          <FormControl
            id="flexSwitchCheckDefault"
            className="form-check form-switch mt-16 mb-8"
          >
            <FormControl.Input
              type="checkbox"
              role="switch"
              {...register("enablePublic")}
              className="form-check-input"
              size="md"
            />
            <FormControl.Label className="form-check-label">
              {i18n(
                "explorer.resource.editModal.access.flexSwitchCheckDefault.label",
              )}
            </FormControl.Label>
          </FormControl>

          <FormControl id="slug">
            <div className="d-flex flex-wrap align-items-center gap-4">
              <div>https://neoconnect.opendigitaleducation.com/</div>

              <div className="flex-fill">
                <Input
                  type="text"
                  defaultValue={getSelectedIResources()[0]?.slug}
                  {...register("safeSlug")}
                  size="md"
                  placeholder={i18n(
                    "explorer.resource.editModal.access.url.extension",
                  )}
                />
              </div>
              <Button
                color="primary"
                onClick={() => {}}
                type="button"
                leftIcon={<Copy />}
                variant="ghost"
                className="text-nowrap"
              >
                {i18n("explorer.resource.editModal.access.url.button")}
              </Button>
            </div>
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
          disabled={!isValid || isSubmitting}
        >
          {i18n(edit ? "save" : "explorer.create")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
