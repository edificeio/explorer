import {
  Modal,
  Button,
  Heading,
  FormControl,
  Label,
  Input,
  ImagePicker,
  useOdeClient,
  TextArea,
} from "@edifice-ui/react";
import { IResource } from "edifice-ts-client";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

import { ActivitiesDropdown } from "./components/ActivitiesDropdown";
import { AgeSelect } from "./components/AgeSelect";
import { LangSelect } from "./components/LangSelect";
import { PublishModalFooter } from "./components/PublishModalFooter";
import { SubjectsDropdown } from "./components/SubjectsDropdown";
import usePublishModal from "./hooks/usePublishModal";

interface PublishModalProps {
  isOpen: boolean;
  resource: IResource;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function PublishModal({
  isOpen,
  resource,
  onSuccess,
  onCancel,
}: PublishModalProps) {
  const { currentApp } = useOdeClient();
  const { t } = useTranslation();

  const {
    control,
    cover,
    formState: { isDirty, isValid, isSubmitting },
    handleDeleteImage,
    handleSubmit,
    handleUploadImage,
    handlePublish,
    register,
    selectActivities,
    selectedActivities,
    selectedSubjectAreas,
    selectSubjects,
  } = usePublishModal({ resource, onSuccess });

  const defaultSelectAgeMinOption = "bpr.form.publication.age.min";
  const defaultSelectAgeMaxOption = "bpr.form.publication.age.max";

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="libraryModal" size="lg">
      <Modal.Header onModalClose={onCancel}>{t("bpr.publish")}</Modal.Header>
      <Modal.Subtitle>{t("bpr.form.tip")}</Modal.Subtitle>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {t("bpr.form.publication.heading.general")}
        </Heading>

        <form id="libraryModalForm" onSubmit={handleSubmit(handlePublish)}>
          <div className="d-block d-md-flex mb-24 gap-24">
            <div style={{ maxWidth: "160px" }}>
              <div className="form-label">
                {t("bpr.form.publication.cover.title")}
              </div>
              <ImagePicker
                app={currentApp}
                src={resource.thumbnail}
                label={t("bpr.form.publication.cover.upload.label")}
                addButtonLabel={t("bpr.form.publication.cover.upload.add")}
                deleteButtonLabel={t(
                  "bpr.form.publication.cover.upload.remove",
                )}
                onUploadImage={handleUploadImage}
                onDeleteImage={handleDeleteImage}
                className="align-self-center"
              />
              {!cover && (
                <p className="form-text is-invalid">
                  <em>
                    {t("bpr.form.publication.cover.upload.required.image")}
                  </em>
                </p>
              )}
            </div>
            <div className="flex-fill">
              <FormControl id="title" className="mb-16" isRequired>
                <Label>{t("bpr.form.publication.title")}</Label>
                <Input
                  type="text"
                  defaultValue={resource.name}
                  {...register("title", { required: true })}
                  placeholder={t("bpr.form.publication.title.placeholder")}
                  size="md"
                  aria-required={true}
                />
              </FormControl>

              <FormControl id="description" isRequired>
                <Label>{t("bpr.form.publication.description")}</Label>
                <TextArea
                  {...register("description", { required: true })}
                  placeholder={t(
                    "bpr.form.publication.description.placeholder",
                  )}
                  size="md"
                />
              </FormControl>
            </div>
          </div>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            {t("bpr.form.publication.heading.infos")}
          </Heading>

          <div className="d-flex flex-column flex-md-row gap-16 row mb-24">
            <ActivitiesDropdown
              control={control}
              selectedActivities={selectedActivities}
              selectActivities={selectActivities}
            />
            <SubjectsDropdown
              control={control}
              selectedSubjectAreas={selectedSubjectAreas}
              selectSubjects={selectSubjects}
            />
            <LangSelect control={control} />
          </div>

          <div className="mb-24">
            <label htmlFor="" className="form-label">
              {t("bpr.form.publication.age")}
            </label>
            <div className="d-flex gap-8">
              <div className="col col-2">
                <AgeSelect
                  control={control}
                  name="ageMin"
                  placeholderOption={defaultSelectAgeMinOption}
                  validate={(value, formValues) =>
                    parseInt(value) <= parseInt(formValues.ageMax)
                  }
                />
              </div>
              <div className="col col-2">
                <AgeSelect
                  control={control}
                  name="ageMax"
                  placeholderOption={defaultSelectAgeMaxOption}
                  validate={(value, formValues) =>
                    parseInt(value) >= parseInt(formValues.ageMin)
                  }
                />
              </div>
            </div>
          </div>

          <div className="mb-24">
            <FormControl id="keywords" isOptional>
              <Label>{t("bpr.form.publication.keywords")}</Label>
              <Input
                type="text"
                {...register("keyWords")}
                size="md"
                placeholder={t("bpr.form.publication.keywords.placeholder")}
              />
            </FormControl>
          </div>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            {t("bpr.form.publication.licence.text")}
          </Heading>

          <PublishModalFooter />
        </form>
      </Modal.Body>
      <Modal.Footer>
        <Button
          color="tertiary"
          onClick={onCancel}
          type="button"
          variant="ghost"
        >
          {t("cancel")}
        </Button>
        <Button
          form="libraryModalForm"
          type="submit"
          color="primary"
          variant="filled"
          isLoading={isSubmitting}
          disabled={!cover || isSubmitting || !isDirty || !isValid}
        >
          {t("bpr.form.submit")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
