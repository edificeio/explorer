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
} from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { type PublishResult } from "ode-ts-client";
import { createPortal } from "react-dom";

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
  const { i18n, currentApp } = useOdeClient();

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

  const defaultSelectLanguageOption = i18n("bpr.form.publication.language");
  const defaultSelectAgeMinOption = i18n("bpr.form.publication.age.min");
  const defaultSelectAgeMaxOption = i18n("bpr.form.publication.age.max");

  return createPortal(
    <Modal isOpen={isOpen} onModalClose={onCancel} id="libraryModal" size="lg">
      <Modal.Header onModalClose={onCancel}>{i18n("bpr.publish")}</Modal.Header>
      <Modal.Subtitle>{i18n("bpr.form.tip")}</Modal.Subtitle>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {i18n("bpr.form.publication.heading.general")}
        </Heading>

        <form id="libraryModalForm" onSubmit={handleSubmit(publish)}>
          <div className="d-flex mb-24 gap-24">
            <div style={{ maxWidth: "160px" }}>
              <div className="form-label">
                {i18n("bpr.form.publication.cover.title")}
              </div>
              <ImagePicker
                app={currentApp}
                src={selectedResources[0]?.thumbnail}
                label={i18n("bpr.form.publication.cover.upload.label")}
                addButtonLabel={i18n("bpr.form.publication.cover.upload.add")}
                deleteButtonLabel={i18n(
                  "bpr.form.publication.cover.upload.remove",
                )}
                onUploadImage={handleUploadImage}
                onDeleteImage={handleDeleteImage}
                className="align-self-center"
              />
              {!cover.image && (
                <p className="form-text is-invalid">
                  <em>
                    {i18n("bpr.form.publication.cover.upload.required.image")}
                  </em>
                </p>
              )}
            </div>
            <div className="flex-fill">
              <FormControl id="title" className="mb-16" isRequired>
                <Label>{i18n("bpr.form.publication.title")}</Label>
                <Input
                  type="text"
                  defaultValue={selectedResources[0]?.name}
                  {...register("title", { required: true })}
                  placeholder={i18n("bpr.form.publication.title.placeholder")}
                  size="md"
                  aria-required={true}
                />
              </FormControl>

              <FormControl id="description" isRequired>
                <Label>{i18n("bpr.form.publication.description")}</Label>
                <Input
                  type="text"
                  {...register("description", { required: true })}
                  placeholder={i18n(
                    "bpr.form.publication.description.placeholder",
                  )}
                  size="md"
                  aria-required={true}
                />
              </FormControl>
            </div>
          </div>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            {i18n("bpr.form.publication.heading.infos")}
          </Heading>

          <div className="d-flex flex-column flex-md-row gap-16 row mb-24">
            <div className="col d-flex">
              <Dropdown
                trigger={
                  <DropdownTrigger
                    title={i18n("bpr.form.publication.type")}
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
                    title={i18n("bpr.form.publication.discipline")}
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
              {i18n("bpr.form.publication.age")}
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
              <Label>{i18n("bpr.form.publication.keywords")}</Label>
              <Input
                type="text"
                {...register("keyWords")}
                size="md"
                placeholder={i18n("bpr.form.publication.keywords.placeholder")}
              />
            </FormControl>
          </div>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            {i18n("bpr.form.publication.licence.text")}
          </Heading>

          <ul className="mb-12">
            <li>
              {i18n("bpr.form.publication.licence.text.1")}
              <img
                className="ms-8 d-inline-block"
                src="/assets/themes/entcore-css-lib/images/cc-by-nc-sa.svg"
                alt="Icone licence Creative
                  Commons"
              />
            </li>
            <li>{i18n("bpr.form.publication.licence.text.2")}</li>
          </ul>

          <Alert type="info" className="mb-12">
            {i18n("bpr.form.publication.licence.text.3")}
          </Alert>

          <Alert type="warning">
            {i18n("bpr.form.publication.licence.text.4")}
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
          {i18n("cancel")}
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
          {i18n("bpr.form.submit")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}

export function PublishModalSuccess({ result }: { result: PublishResult }) {
  const { i18n } = useOdeClient();

  return (
    <>
      <h3 className="pt-24">
        {i18n("bpr.form.publication.response.success.title")}
      </h3>
      <p className="pt-24">
        {i18n("bpr.form.publication.response.success.content.1")}
      </p>
      <p className="pt-24">
        {i18n("bpr.form.publication.response.success.content.2")}
      </p>
      {result.details.front_url && (
        <p className="pt-24 pb-24">
          <a
            className="button right-magnet"
            href={result.details.front_url}
            target="_blank"
            rel="noreferrer"
          >
            {i18n("bpr.form.publication.response.success.button")}
          </a>
        </p>
      )}
    </>
  );
}

export function PublishModalError({ formData }: { formData: InputProps }) {
  const { i18n } = useOdeClient();

  const regexInput = (value: string) => {
    return value.match(/^\s/);
  };

  return (
    <>
      <h3 className="pt-24">
        {i18n("bpr.form.publication.response.error.title")}
      </h3>
      <p className="pt-24 pb-24">
        <strong>{i18n("bpr.form.publication.response.error.content")}</strong>
      </p>
      <ul>
        {regexInput(formData.title) && (
          <li className="pt-2 pb-2">
            <strong>{i18n("bpr.form.publication.response.empty.title")}</strong>
          </li>
        )}
        {regexInput(formData.description) && (
          <li className="pt-2 pb-2">
            <strong>
              {i18n("bpr.form.publication.response.empty.description")}
            </strong>
          </li>
        )}
      </ul>
    </>
  );
}
