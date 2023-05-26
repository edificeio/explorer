import {
  Alert,
  Button,
  FormControl,
  FormText,
  Heading,
  ImagePicker,
  Input,
  Label,
  Modal,
} from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { Copy } from "@ode-react-ui/icons";
import { createPortal } from "react-dom";

import useEditResourceModal from "../hooks/useEditResourceModal";
import { useSelectedResources } from "~store/store";

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
  const { i18n, appCode, currentApp } = useOdeClient();

  const selectedResources = useSelectedResources();
  const resource = selectedResources[0];
  const {
    slug,
    formId,
    isValid,
    isSubmitting,
    disableSlug,
    versionSlug,
    correctSlug,
    register,
    handleSubmit,
    onSubmit,
    handleUploadImage,
    onCopyToClipBoard,
    onSlugChange,
    onPublicChange,
  } = useEditResourceModal({
    resource,
    onSuccess,
    onCancel,
  });
  const refPublic = `${new Date().getTime()}_${disableSlug}`;
  const refSlug = `${versionSlug}_slug`;
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
              app={currentApp}
              src={resource?.thumbnail}
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
                  defaultValue={edit ? resource?.name : ""}
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
                  defaultValue={edit ? resource?.description : ""}
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
            {appCode}
          </Heading>

          <Alert type="info">
            {i18n("explorer.resource.editModal.access.alert")}
          </Alert>

          <FormControl
            id="flexSwitchCheckDefault"
            className="form-switch d-flex gap-8 mt-16 mb-8"
          >
            <FormControl.Input
              type="checkbox"
              role="switch"
              key={refPublic}
              {...register("enablePublic", {
                value: resource.public!,
                onChange: (e) => onPublicChange(e.target.checked),
              })}
              className="form-check-input mt-0"
              size="md"
            />
            <FormControl.Label className="form-check-label mb-0">
              {i18n(
                "explorer.resource.editModal.access.flexSwitchCheckDefault.label",
              )}
            </FormControl.Label>
          </FormControl>

          <FormControl id="slug" status={correctSlug ? "invalid" : undefined}>
            <div className="d-flex flex-wrap align-items-center gap-4">
              <div>{window.location.origin}/</div>

              <div className="flex-fill">
                <Input
                  type="text"
                  key={refSlug}
                  {...register("safeSlug", {
                    validate: {
                      required: (value) => {
                        if (!value && !disableSlug)
                          return "Requis lorsque la checkbox 'Accessible publiquement via une URL est cochée'";
                        return true;
                      },
                    },
                    disabled: disableSlug,
                    value: slug,
                    onChange: (e) => onSlugChange(e.target.value),
                  })}
                  size="md"
                  placeholder={i18n(
                    "explorer.resource.editModal.access.url.extension",
                  )}
                />
                {correctSlug && (
                  <div className="position-absolute">
                    <FormText>
                      Choisissez une autre URL pour votre blog
                    </FormText>
                  </div>
                )}
              </div>
              <Button
                color="primary"
                disabled={disableSlug}
                onClick={() => {
                  onCopyToClipBoard(resource.slug!);
                }}
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
