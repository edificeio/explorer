import { useRef } from "react";

import {
  Avatar,
  Button,
  Checkbox,
  FormControl,
  Heading,
  IconButton,
  Input,
  Modal,
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
import { type ShareRight } from "ode-ts-client";
import { createPortal } from "react-dom";

import ShareResourceModalFooter from "./ShareResourceModalFooter";
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
    myAvatar,
    idBookmark,
    shareRights,
    shareRightActions,
    showBookmarkInput,
    searchInputValue,
    searchResults,
    bookmarkName,
    setBookmarkName,
    saveBookmark,
    canSave,
    toggleBookmarkInput,
    handleActionCheckbox,
    handleShare,
    handleDeleteRow,
    handleSearchInputChange,
    handleSearchInputKeyUp,
    handleSearchButtonClick,
    handleSearchResultClick,
    hasRight,
  } = useShareResourceModal({ onSuccess, onCancel });
  const { i18n } = useOdeClient();
  const refBookmark = useRef<HTMLInputElement>(null);
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
                {shareRightActions.map((shareRightAction) => (
                  <th
                    key={shareRightAction.displayName}
                    scope="col"
                    className="text-center"
                  >
                    {shareRightAction.displayName}
                  </th>
                ))}
                <th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <th scope="row">
                  <Avatar
                    alt="alternative text"
                    size="xs"
                    src={myAvatar}
                    variant="circle"
                  />
                </th>
                <td>{i18n("share.me")}</td>
                {shareRightActions.map((shareRightAction) => (
                  <td
                    key={shareRightAction.displayName}
                    style={{ width: "80px" }}
                    className="text-center"
                  >
                    <Checkbox checked={true} disabled />
                  </td>
                ))}
                <td></td>
              </tr>
              {shareRights?.rights.map((shareRight: ShareRight) => (
                <tr key={shareRight.id}>
                  <th scope="row">
                    <Avatar
                      alt="alternative text"
                      size="xs"
                      src={shareRight.avatarUrl}
                      variant="circle"
                    />
                  </th>
                  <td>{shareRight.displayName}</td>
                  {shareRightActions.map((shareRightAction) => (
                    <td
                      key={shareRightAction.displayName}
                      style={{ width: "80px" }}
                      className="text-center"
                    >
                      <Checkbox
                        checked={hasRight(shareRight, shareRightAction)}
                        onChange={() =>
                          handleActionCheckbox(shareRight, shareRightAction.id)
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
                      onClick={() => handleDeleteRow(shareRight.id)}
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
                    key={idBookmark}
                    ref={refBookmark}
                    onChange={() => {
                      setBookmarkName(() => refBookmark.current?.value || "");
                    }}
                    placeholder="Saisir le nom du favori"
                    size="sm"
                    type="text"
                  />
                </div>
                <Button
                  type="button"
                  color="primary"
                  variant="ghost"
                  disabled={bookmarkName.length === 0}
                  leftIcon={<Save />}
                  onClick={(_) => {
                    saveBookmark(refBookmark.current!.value!);
                  }}
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
            onChange={handleSearchInputChange}
            onKeyUp={handleSearchInputKeyUp}
          />
          <SearchButton
            aria-label="search"
            icon={<Search />}
            onClick={handleSearchButtonClick}
          />
        </FormControl>
        {searchInputValue && (
          <div>
            <ul className="ps-0" style={{ listStyle: "none" }}>
              {searchResults.length === 0 && (
                <li
                  key="noresult"
                  className="d-flex p-8"
                  style={{ cursor: "pointer", maxWidth: "fit-content" }}
                >
                  <span className="ps-8">Aucun résultat</span>
                </li>
              )}
              {searchResults.map((searchResult) => (
                <li
                  key={searchResult.id}
                  className="d-flex p-8"
                  onClick={(event) =>
                    handleSearchResultClick(event, searchResult)
                  }
                  style={{ cursor: "pointer", maxWidth: "fit-content" }}
                >
                  <Avatar
                    alt="alternative text"
                    size="xs"
                    src={searchResult.avatarUrl}
                    variant="circle"
                  />
                  <span className="ps-8">{searchResult.displayName}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
        <ShareResourceModalFooter />
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
          disabled={!canSave()}
        >
          Partager
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
