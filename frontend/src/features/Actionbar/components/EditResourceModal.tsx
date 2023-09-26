import {
  Button,
  FormControl,
  Heading,
  ImagePicker,
  Input,
  TextArea,
  Label,
  Modal,
  useOdeClient,
} from "@edifice-ui/react";
import { APP } from "edifice-ts-client";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

import { BlogPublic } from "./BlogPublic";
import useEditResourceModal from "../hooks/useEditResourceModal";
import { useActions } from "~/services/queries";
import { useSelectedResources } from "~/store";
import { isActionAvailable } from "~/utils/isActionAvailable";

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
  const { appCode, currentApp } = useOdeClient();

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
    edit,
    onSuccess,
    onCancel,
  });

  const { data: actions } = useActions();

  const { t } = useTranslation();

  return createPortal(
    <Modal
      id="resource_edit_modal"
      size="lg"
      isOpen={isOpen}
      onModalClose={onCancel}
    >
      <Modal.Header onModalClose={onCancel}>
        {t(
          edit
            ? "explorer.resource.editModal.header.edit"
            : "explorer.resource.editModal.header.create",
        )}
      </Modal.Header>

      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {t("explorer.resource.editModal.heading.general")}
        </Heading>

        <form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <div className="d-flex gap-16 mb-24">
            <div>
              <ImagePicker
                app={currentApp}
                src={resource?.thumbnail}
                label={t("explorer.imagepicker.label")}
                addButtonLabel={t("explorer.imagepicker.button.add")}
                deleteButtonLabel={t("explorer.imagepicker.button.delete")}
                onUploadImage={handleUploadImage}
                onDeleteImage={handleDeleteImage}
                className="align-self-center mt-8"
              />
            </div>

            <div className="col">
              <FormControl id="title" className="mb-16" isRequired>
                <Label>{t("title")}</Label>
                <Input
                  type="text"
                  defaultValue={edit ? resource?.name : ""}
                  {...register("title", {
                    required: true,
                    pattern: {
                      value: /[^ ]/,
                      message: "invalid title",
                    },
                  })}
                  placeholder={t(
                    "explorer.resource.editModal.title.placeholder",
                  )}
                  size="md"
                  aria-required={true}
                />
              </FormControl>
              <FormControl id="description" isOptional>
                <Label>{t("description")}</Label>
                <TextArea
                  defaultValue={edit ? resource?.description : ""}
                  {...register("description")}
                  placeholder={t(
                    "explorer.resource.editModal.description.placeholder",
                  )}
                  size="md"
                />
              </FormControl>
            </div>
          </div>

          {appCode === APP.BLOG &&
            isActionAvailable({ workflow: "createPublic", actions }) && (
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
          {t("explorer.cancel")}
        </Button>
        <Button
          form={formId}
          type="submit"
          color="primary"
          variant="filled"
          disabled={!isValid || isSubmitting}
        >
          {t(edit ? "save" : "explorer.create")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
