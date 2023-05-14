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
  SelectList,
} from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import {
  Bookmark,
  Close,
  InfoCircle,
  RafterDown,
  Save,
  Search,
} from "@ode-react-ui/icons";
import { type ShareRight } from "ode-ts-client/dist/share/interface";
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
    showBookmarkMembers,
    currentIsAuthor,
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
    handleSearchResultsChange,
    handleBookmarkMembersToggle,
    hasRight,
    showShareRightLine,
  } = useShareResourceModal({ onSuccess, onCancel });
  const { i18n } = useOdeClient();
  const refBookmark = useRef<HTMLInputElement>(null);
  return createPortal(
    <Modal id="share_modal" size="lg" isOpen={isOpen} onModalClose={onCancel}>
      <Modal.Header onModalClose={onCancel}>{i18n("share.title")}</Modal.Header>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {i18n("explorer.modal.share.usersWithAccess")}
        </Heading>

        <div className="table-responsive">
          <table className="table  border align-middle mb-0">
            <thead className="bg-secondary text-white">
              <tr>
                <th scope="col" className="w-32"></th>
                <th scope="col"></th>
                {shareRightActions.map((shareRightAction) => (
                  <th
                    key={shareRightAction.displayName}
                    scope="col"
                    className="text-center"
                  >
                    {i18n(shareRightAction.displayName)}
                  </th>
                ))}
                <th scope="col"></th>
              </tr>
            </thead>
            <tbody>
              {currentIsAuthor() && (
                <tr>
                  <th scope="row">
                    <Avatar
                      alt={i18n("explorer.modal.share.avatar.me.alt")}
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
              )}
              {shareRights?.rights.map((shareRight: ShareRight) => {
                return (
                  showShareRightLine(shareRight) && (
                    <tr
                      key={shareRight.id}
                      className={shareRight.isBookmarkMember ? "bg-light" : ""}
                    >
                      <td scope="row">
                        {shareRight.type !== "sharebookmark" && (
                          <Avatar
                            alt={i18n("explorer.modal.share.avatar.shared.alt")}
                            size="xs"
                            src={shareRight.avatarUrl}
                            variant="circle"
                          />
                        )}

                        {shareRight.type === "sharebookmark" && <Bookmark />}
                      </td>
                      <td>
                        <div className="d-flex">
                          {shareRight.type === "sharebookmark" && (
                            <Button
                              color="tertiary"
                              rightIcon={
                                <RafterDown
                                  title="Show"
                                  className="w-16 min-w-0"
                                  style={{
                                    transition: "rotate 0.2s ease-out",
                                    rotate: showBookmarkMembers
                                      ? "-180deg"
                                      : "0deg",
                                  }}
                                />
                              }
                              type="button"
                              variant="ghost"
                              className="fw-normal ps-0"
                              onClick={handleBookmarkMembersToggle}
                            >
                              {shareRight.displayName}
                            </Button>
                          )}
                          {shareRight.type !== "sharebookmark" &&
                            shareRight.displayName}
                        </div>
                      </td>
                      {shareRightActions.map((shareRightAction) => (
                        <td
                          key={shareRightAction.displayName}
                          style={{ width: "80px" }}
                          className="text-center"
                        >
                          <Checkbox
                            checked={hasRight(shareRight, shareRightAction)}
                            onChange={() =>
                              handleActionCheckbox(
                                shareRight,
                                shareRightAction.id,
                              )
                            }
                          />
                        </td>
                      ))}
                      <td>
                        {!shareRight.isBookmarkMember && (
                          <IconButton
                            aria-label="Delete"
                            color="tertiary"
                            icon={<Close />}
                            type="button"
                            variant="ghost"
                            title="Delete"
                            onClick={() => handleDeleteRow(shareRight)}
                          />
                        )}
                      </td>
                    </tr>
                  )
                );
              })}
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
            {i18n("share.save.sharebookmark")}
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
                    placeholder={i18n(
                      "explorer.modal.share.sharebookmark.placeholder",
                    )}
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
                  {i18n("explorer.modal.share.sharebookmark.save")}
                </Button>
              </FormControl>
            </div>
          )}
        </div>

        <hr />

        <Heading headingStyle="h4" level="h3" className="mb-16 d-flex">
          {i18n("explorer.modal.share.search")} <InfoCircle className="ms-8" />
        </Heading>

        <FormControl className="input-group max-w-512" id="search">
          <Input
            noValidationIcon
            placeholder={i18n("explorer.modal.share.search.placeholder")}
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
        {searchResults?.length > 0 && (
          <div className="bg-white shadow rounded-4 d-block show py-12 px-8 max-w-512">
            <SelectList
              options={searchResults}
              hideCheckbox={true}
              isMonoSelection={true}
              onChange={handleSearchResultsChange}
            ></SelectList>
          </div>
        )}
        {searchInputValue.length > 0 && searchResults.length === 0 && (
          <div className="p-8">{i18n("portal.no.result")}</div>
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
          {i18n("share")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
