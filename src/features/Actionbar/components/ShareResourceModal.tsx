import {
  Avatar,
  Button,
  Checkbox,
  FormControl,
  Heading,
  IconButton,
  Input,
  Modal,
  Radio,
  SearchButton,
  useOdeClient,
} from "@ode-react-ui/core";
import {
  Bookmark,
  Close,
  InfoCircle,
  RafterDown,
  Save,
  Search,
} from "@ode-react-ui/icons";
import { createPortal } from "react-dom";

import useShareResourceModal from "../hooks/useShareResourceModal";

interface ShareResourceModalProps {
  isOpen: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function ShareResourceModal({
  isOpen,
  onSuccess,
  onCancel,
}: ShareResourceModalProps) {
  const {
    items,
    actions,
    showBookmarkInput,
    radioPublicationValue,
    toggleBookmarkInput,
    handleRadioPublicationChange,
    handleActionCheckbox,
    handleShare,
  } = useShareResourceModal({ onSuccess, onCancel });
  const { i18n } = useOdeClient();

  return createPortal(
    <Modal id="share_modal" size="lg" isOpen={isOpen} onModalClose={onCancel}>
      <Modal.Header onModalClose={onCancel}>Partager avec ...</Modal.Header>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          Utilisateurs ayant accès
        </Heading>

        <div className="table-responsive">
          <table className="table  border align-middle mb-0">
            <thead className="bg-secondary text-white">
              <tr>
                <th scope="col"></th>
                <th scope="col"></th>
                {actions.map((action) => (
                  <th
                    key={action.displayName}
                    scope="col"
                    className="text-center"
                  >
                    {action.displayName}
                  </th>
                ))}
                <th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item: any) => (
                <tr key={item.id}>
                  <th scope="row">
                    <Avatar
                      alt="alternative text"
                      size="xs"
                      src={item.avatarUrl}
                      variant="circle"
                    />
                  </th>
                  <td>
                    {item.type === "user" && item.username}
                    {item.type === "group" && item.name}
                  </td>
                  {actions.map((action) => (
                    <td
                      key={action.displayName}
                      style={{ width: "80px" }}
                      className="text-center"
                    >
                      <Checkbox
                        checked={item.actions[action.displayName]}
                        onChange={(e) =>
                          handleActionCheckbox(item, action.displayName)
                        }
                      />
                    </td>
                  ))}
                  <td>
                    <IconButton
                      aria-label="Delete"
                      color="tertiary"
                      icon={<Close />}
                      type="button"
                      variant="ghost"
                      title="Delete"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-16">
          <Button
            color="tertiary"
            leftIcon={<Bookmark />}
            rightIcon={
              <RafterDown
                title="Show"
                className="w-16 min-w-0"
                style={{
                  transition: "rotate 0.2s ease-out",
                  rotate: showBookmarkInput ? "-180deg" : "0deg",
                }}
              />
            }
            type="button"
            variant="ghost"
            className="fw-normal"
            onClick={() => toggleBookmarkInput(!showBookmarkInput)}
          >
            Enregistrer comme favori de partage
          </Button>
          {showBookmarkInput && (
            <div className="mt-16">
              <FormControl
                id="bookmarkName"
                className="d-flex flex-wrap align-items-center gap-16"
              >
                <div className="flex-fill">
                  <FormControl.Input
                    placeholder="Saisir le nom du favori"
                    size="sm"
                    type="text"
                  />
                </div>
                <Button
                  type="button"
                  color="primary"
                  variant="ghost"
                  leftIcon={<Save />}
                  className="text-nowrap"
                >
                  Enregistrer favori
                </Button>
              </FormControl>
            </div>
          )}
        </div>

        <hr />

        <Heading headingStyle="h4" level="h3" className="mb-16 d-flex">
          Rechercher des utilisateurs <InfoCircle className="ms-8" />
        </Heading>

        <FormControl className="input-group max-w-512" id="search">
          <Input
            noValidationIcon
            placeholder="nom d’utilisateurs, groupes, favoris"
            size="md"
            type="search"
          />
          <SearchButton aria-label="search" icon={<Search />} type="submit" />
        </FormControl>

        <hr />

        <Heading headingStyle="h4" level="h3" className="mb-16">
          Circuit de publication des billets
        </Heading>

        <Radio
          label="Publication immédiate"
          id="publication-now"
          name="publication"
          value="now"
          model={radioPublicationValue}
          onChange={handleRadioPublicationChange}
        />
        <Radio
          label="Billets soumis à validation"
          id="publication-validate"
          name="publication"
          value="validate"
          model={radioPublicationValue}
          onChange={handleRadioPublicationChange}
        />
      </Modal.Body>
      <Modal.Footer>
        <Button
          type="button"
          color="tertiary"
          variant="ghost"
          onClick={onCancel}
        >
          {i18n("explorer.cancel")}
        </Button>

        <Button
          type="button"
          color="primary"
          variant="filled"
          onClick={handleShare}
        >
          Partager
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
