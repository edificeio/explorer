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

import { BlogPublic } from "./BlogPublic";
import useEditResourceModal from "../hooks/useEditResourceModal";
import { useActions } from "~/services/queries";
import { isActionAvailable } from "~/shared/utils/isActionAvailable";
import { useSelectedResources } from "~/store";

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
    handleDeleteImage,
    onCopyToClipBoard,
    onSlugChange,
    onPublicChange,
  } = useEditResourceModal({
    resource,
    onSuccess,
    onCancel,
  });

  const { data: actions } = useActions();

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
              onDeleteImage={handleDeleteImage}
              className="align-self-center"
            />

            <div className="col">
              <FormControl id="title" className="mb-16" isRequired>
                <Label>{i18n("title")}</Label>
                <Input
                  type="text"
                  defaultValue={edit ? resource?.name : ""}
                  {...register("title", {
                    required: true,
                    pattern: {
                      value: /^\S/,
                      message: "invalid title",
                    },
                  })}
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

          {isActionAvailable({ workflow: "createPublic", actions }) && (
            <BlogPublic
              appCode={appCode}
              correctSlug={correctSlug}
              disableSlug={disableSlug}
              onCopyToClipBoard={onCopyToClipBoard}
              onPublicChange={onPublicChange}
              onSlugChange={onSlugChange}
              resource={resource}
              slug={slug}
              versionSlug={versionSlug}
              register={register}
            />
          )}
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
