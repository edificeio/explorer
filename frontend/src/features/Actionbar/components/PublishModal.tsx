import {
  Alert,
  Modal,
  Button,
  Heading,
  FormControl,
  Label,
  Input,
  ImagePicker,
  Select,
  Dropdown,
  DropdownTrigger,
  SelectList,
  useOdeClient,
  usePaths,
  TextArea,
} from "@edifice-ui/react";
import { type PublishResult } from "edifice-ts-client";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

import usePublishModal, { InputProps } from "../hooks/usePublishModal";
import usePublishLibraryModalOptions from "../hooks/usePublishModalOptions";
import { useSelectedResources } from "~/store";

interface PublishModalProps {
  isOpen: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function PublishModal({
  isOpen,
  onSuccess,
  onCancel,
}: PublishModalProps) {
  const { currentApp } = useOdeClient();
  const [imagePath] = usePaths();
  const { t } = useTranslation();

  const {
    register,
    handleSubmit,
    publish,
    formState: { isDirty, isValid },
    handleUploadImage,
    selectedActivities,
    setSelectedActivities,
    selectedSubjectAreas,
    setSelectedSubjectAreas,
    handleDeleteImage,
    loaderPublish,
    cover,
  } = usePublishModal({ onSuccess });

  const selectedResources = useSelectedResources();

  const {
    activityTypeOptions,
    subjectAreaOptions,
    languageOptions,
    ageOptions,
  } = usePublishLibraryModalOptions();

  const defaultSelectLanguageOption = t("bpr.form.publication.language");
  const defaultSelectAgeMinOption = t("bpr.form.publication.age.min");
  const defaultSelectAgeMaxOption = t("bpr.form.publication.age.max");

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="libraryModal" size="lg">
      <Modal.Header onModalClose={onCancel}>{t("bpr.publish")}</Modal.Header>
      <Modal.Subtitle>{t("bpr.form.tip")}</Modal.Subtitle>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {t("bpr.form.publication.heading.general")}
        </Heading>

        <form id="libraryModalForm" onSubmit={handleSubmit(publish)}>
          <div className="d-flex mb-24 gap-24">
            <div style={{ maxWidth: "160px" }}>
              <div className="form-label">
                {t("bpr.form.publication.cover.title")}
              </div>
              <ImagePicker
                app={currentApp}
                src={selectedResources[0]?.thumbnail}
                label={t("bpr.form.publication.cover.upload.label")}
                addButtonLabel={t("bpr.form.publication.cover.upload.add")}
                deleteButtonLabel={t(
                  "bpr.form.publication.cover.upload.remove",
                )}
                onUploadImage={handleUploadImage}
                onDeleteImage={handleDeleteImage}
                className="align-self-center"
              />
              {!cover.image && (
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
                  defaultValue={selectedResources[0]?.name}
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
            <div className="col d-flex">
              <Dropdown
                trigger={
                  <DropdownTrigger
                    title={t("bpr.form.publication.type")}
                    size="md"
                    grow={true}
                    badgeContent={selectedActivities?.length}
                  />
                }
                content={
                  <SelectList
                    options={activityTypeOptions}
                    model={selectedActivities}
                    onChange={(activities: Array<string | number>) =>
                      setSelectedActivities(activities)
                    }
                  />
                }
              />
            </div>
            <div className="col d-flex">
              <Dropdown
                trigger={
                  <DropdownTrigger
                    title={t("bpr.form.publication.discipline")}
                    size="md"
                    grow={true}
                    badgeContent={selectedSubjectAreas?.length}
                  />
                }
                content={
                  <SelectList
                    options={subjectAreaOptions}
                    model={selectedSubjectAreas}
                    onChange={(subjectAreas: Array<string | number>) =>
                      setSelectedSubjectAreas(subjectAreas)
                    }
                  />
                }
              />
            </div>
            <div className="col">
              <FormControl id="language" isRequired>
                <Select
                  {...register("language", {
                    required: true,
                    validate: (value) => value !== defaultSelectLanguageOption,
                  })}
                  options={languageOptions}
                  placeholderOption={defaultSelectLanguageOption}
                  defaultValue={defaultSelectLanguageOption}
                  aria-required={true}
                />
              </FormControl>
            </div>
          </div>

          <div className="mb-24">
            <label htmlFor="" className="form-label">
              {t("bpr.form.publication.age")}
            </label>
            <div className="d-flex">
              <div className="me-16">
                <FormControl id="ageMin" isRequired>
                  <Select
                    {...register("ageMin", {
                      required: true,
                      validate: (value, formValues) =>
                        parseInt(value) <= parseInt(formValues.ageMax),
                    })}
                    options={ageOptions}
                    placeholderOption={defaultSelectAgeMinOption}
                    defaultValue={defaultSelectAgeMinOption}
                    aria-required={true}
                  />
                </FormControl>
              </div>
              <div>
                <FormControl id="ageMax" isRequired>
                  <Select
                    {...register("ageMax", {
                      required: true,
                      validate: (value, formValues) =>
                        parseInt(value) >= parseInt(formValues.ageMin),
                    })}
                    options={ageOptions}
                    placeholderOption={defaultSelectAgeMaxOption}
                    defaultValue={defaultSelectAgeMaxOption}
                    aria-required={true}
                  />
                </FormControl>
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

          <ul className="mb-12">
            <li>
              {t("bpr.form.publication.licence.text.1")}
              <img
                className="ms-8 d-inline-block"
                src={`${imagePath}image-cc-by-nc-sa.svg`}
                alt="Icone licence Creative
                  Commons"
              />
            </li>
            <li>{t("bpr.form.publication.licence.text.2")}</li>
          </ul>

          <Alert type="info" className="mb-12">
            {t("bpr.form.publication.licence.text.3")}
          </Alert>

          <Alert type="warning">
            {t("bpr.form.publication.licence.text.4")}
          </Alert>
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
          isLoading={loaderPublish}
          disabled={
            !cover.image ||
            loaderPublish ||
            !isDirty ||
            !isValid ||
            selectedActivities?.length === 0 ||
            selectedSubjectAreas?.length === 0
          }
        >
          {t("bpr.form.submit")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

export function PublishModalSuccess({ result }: { result: PublishResult }) {
  const { t } = useTranslation();

  return (
    <>
      <h3 className="pt-24">
        {t("bpr.form.publication.response.success.title")}
      </h3>
      <p className="pt-24">
        {t("bpr.form.publication.response.success.content.1")}
      </p>
      <p className="pt-24">
        {t("bpr.form.publication.response.success.content.2")}
      </p>
      {result.details.front_url && (
        <p className="pt-24 pb-24">
          <a
            className="button right-magnet"
            href={result.details.front_url}
            target="_blank"
            rel="noreferrer"
          >
            {t("bpr.form.publication.response.success.button")}
          </a>
        </p>
      )}
    </>
  );
}

export function PublishModalError({ formData }: { formData: InputProps }) {
  const { t } = useTranslation();

  const regexInput = (value: string) => {
    return value.match(/^\s/);
  };

  return (
    <>
      <h3 className="pt-24">
        {t("bpr.form.publication.response.error.title")}
      </h3>
      <p className="pt-24 pb-24">
        <strong>{t("bpr.form.publication.response.error.content")}</strong>
      </p>
      <ul>
        {regexInput(formData.title) && (
          <li className="pt-2 pb-2">
            <strong>{t("bpr.form.publication.response.empty.title")}</strong>
          </li>
        )}
        {regexInput(formData.description) && (
          <li className="pt-2 pb-2">
            <strong>
              {t("bpr.form.publication.response.empty.description")}
            </strong>
          </li>
        )}
      </ul>
    </>
  );
}
