import { useExplorerContext } from "@contexts/index";
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
} from "@ode-react-ui/core";

import usePublishModal from "../hooks/usePublishModal";
import usePublishLibraryModalOptions from "../hooks/usePublishModalOptions";

interface LibraryModalProps {
  isOpen: boolean;
  onSuccess?: () => void;
  onCancel?: () => void;
}

export default function PublishModal({
  isOpen,
  onSuccess = () => {},
  onCancel = () => {},
}: LibraryModalProps) {
  const {
    selectedResources,
    register,
    handleSubmit,
    publish,
    formState: { isDirty, isValid },
    handleUploadImage,
  } = usePublishModal({ onSuccess });

  const {
    activityTypeOptions,
    subjectAreaOptions,
    languageOptions,
    ageOptions,
  } = usePublishLibraryModalOptions();

  const { i18n, app } = useExplorerContext();

  return (
    <Modal isOpen={isOpen} onModalClose={onCancel} id="libraryModal" size="lg">
      <Modal.Header onModalClose={onCancel}>
        {i18n("explorer.library.title")}
      </Modal.Header>
      <Modal.Subtitle>{i18n("explorer.library.subtitle")}</Modal.Subtitle>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          Général
        </Heading>

        <form id="libraryModalForm" onSubmit={handleSubmit(publish)}>
          <FormControl id="title" className="mb-16" isRequired>
            <Label>Titre</Label>
            <Input
              type="text"
              defaultValue={selectedResources[0]?.name}
              {...register("title", { required: true })}
              placeholder="Nom de la ressource"
              size="md"
              aria-required={true}
            />
          </FormControl>

          <div className="mb-24">
            <div className="form-label">Image d'illustration</div>
            <ImagePicker
              label="Upload an image"
              appCode={app?.displayName}
              addButtonLabel="Add image"
              deleteButtonLabel="Delete image"
              onUploadImage={handleUploadImage}
              onDeleteImage={() => {}}
              className="align-self-center"
            />
          </div>

          <FormControl id="description" isRequired>
            <Label>Description et contexte pédagogique</Label>
            <Input
              type="text"
              {...register("description", { required: true })}
              placeholder="Description de la ressource"
              size="md"
              aria-required={true}
            />
          </FormControl>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            Informations sur le contenu
          </Heading>

          <div className="row mb-24">
            <div className="col">
              <FormControl id="activityType" isRequired>
                <Select
                  label="Type d’activité"
                  {...register("activityType", { required: true })}
                  options={activityTypeOptions}
                  placeholderOption="Sélectionner"
                  aria-required={true}
                />
              </FormControl>
            </div>
            <div className="col">
              <FormControl id="subjectArea" isRequired>
                <Select
                  label="Discipline"
                  {...register("subjectArea", { required: true })}
                  options={subjectAreaOptions}
                  placeholderOption="Sélectionner"
                  aria-required={true}
                />
              </FormControl>
            </div>
            <div className="col">
              <FormControl id="language" isRequired>
                <Select
                  id="language"
                  label="Langue"
                  {...register("language", { required: true })}
                  options={languageOptions}
                  placeholderOption="Sélectionner"
                  aria-required={true}
                />
              </FormControl>
            </div>
          </div>

          <div className="mb-24">
            <label htmlFor="" className="form-label">
              Âge conseillé des élèves
            </label>
            <div className="d-flex">
              <div className="me-16">
                <FormControl id="ageMin" isRequired>
                  <Select
                    defaultValue="Age min."
                    id="ageMin"
                    {...register("ageMin")}
                    options={ageOptions}
                    placeholderOption="Age min."
                    aria-required={true}
                  />
                </FormControl>
              </div>
              <div>
                <FormControl id="ageMax" isRequired>
                  <Select
                    defaultValue="Age max."
                    id="ageMax"
                    {...register("ageMax")}
                    options={ageOptions}
                    placeholderOption="Age max."
                    aria-required={true}
                  />
                </FormControl>
              </div>
            </div>
          </div>

          <div className="mb-24">
            <FormControl id="keywords" isOptional>
              <Label>Mots-clefs (5 max), séparés par des virgules</Label>
              <Input
                type="text"
                {...register("keyWords")}
                size="md"
                placeholder="Mots clés"
              />
            </FormControl>
          </div>

          <hr />

          <Heading headingStyle="h4" level="h3" className="mb-16">
            En cliquant sur publier
          </Heading>

          <ul className="mb-12">
            <li>
              J'accepte que mon activité soit publiée sous licence Creative
              Commons
              <img
                className="ms-8 d-inline-block"
                src="/assets/creative-commons.png"
                alt="Icone licence Creative
                  Commons"
              />
            </li>
            <li>
              J'accepte d'être cité en tant qu'auteur, que le nom de mon
              établissement soit affiché ainsi qu'un aperçu de mon avatar. Cela
              permettra aux enseignants de la communauté d'échanger plus
              facilement avec vous de manière bienveillante !
            </li>
          </ul>

          <Alert type="info" className="mb-12">
            Si votre contenu comporte des commentaires, ceux-ci ne seront pas
            publiés dans la Bibliothèque.
          </Alert>

          <Alert type="warning">
            Les billets actuellement en brouillon et les billets ajoutés après
            la publication du Blog dans la Bibliothèque ne seront pas visibles.
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
          disabled={!isDirty || !isValid}
        >
          {i18n("publish")}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
