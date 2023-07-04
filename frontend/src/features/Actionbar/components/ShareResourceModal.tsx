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
  SelectList,
  Loading,
  Tooltip,
  VisuallyHidden,
} from "@ode-react-ui/components";
import {
  Bookmark,
  Close,
  InfoCircle,
  RafterDown,
  Save,
} from "@ode-react-ui/icons";
import { ShareRight } from "ode-ts-client";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

import ShareResourceModalFooter from "./ShareResourceModalFooter";
import useShareResourceModal from "../hooks/useShareResourceModal";
import useShareResourceModalFooterBlog from "../hooks/useShareResourceModalFooterBlog";

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
    payloadUpdatePublishType,
    radioPublicationValue,
    handleRadioPublicationChange,
  } = useShareResourceModalFooterBlog();
  const {
    myAvatar,
    idBookmark,
    shareRights,
    shareRightActions,
    showBookmarkInput,
    searchResults,
    bookmarkName,
    showBookmarkMembers,
    searchPending,
    currentIsAuthor,
    setBookmarkName,
    saveBookmark,
    canSave,
    toggleBookmarkInput,
    handleActionCheckbox,
    handleShare,
    handleDeleteRow,
    handleSearchInputChange,
    handleSearchResultsChange,
    showSearchNoResults,
    showSearchAdmlHint,
    showSearchLoading,
    handleBookmarkMembersToggle,
    hasRight,
    showShareRightLine,
  } = useShareResourceModal({ payloadUpdatePublishType, onSuccess, onCancel });

  const { t } = useTranslation();
  const refBookmark = useRef<HTMLInputElement>(null);
  return createPortal(
    <Modal id="share_modal" size="lg" isOpen={isOpen} onModalClose={onCancel}>
      <Modal.Header onModalClose={onCancel}>{t("share.title")}</Modal.Header>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {t("explorer.modal.share.usersWithAccess")}
        </Heading>

        <div className="table-responsive">
          <table className="table border align-middle mb-0">
            <thead className="bg-secondary text-white">
              <tr>
                <th scope="col" className="w-32">
                  <VisuallyHidden>
                    {t("explorer.modal.share.avatar.shared.alt")}
                  </VisuallyHidden>
                </th>
                <th scope="col">
                  <VisuallyHidden>
                    {t("explorer.modal.share.search.placeholder")}
                  </VisuallyHidden>
                </th>
                {shareRightActions.map((shareRightAction) => (
                  <th
                    key={shareRightAction.displayName}
                    scope="col"
                    className="text-center"
                  >
                    {t(shareRightAction.displayName)}
                  </th>
                ))}
                <th scope="col">
                  <VisuallyHidden>{t("close")}</VisuallyHidden>
                </th>
              </tr>
            </thead>
            <tbody>
              {currentIsAuthor() && (
                <tr>
                  <th scope="row">
                    <Avatar
                      alt={t("explorer.modal.share.avatar.me.alt")}
                      size="xs"
                      src={myAvatar}
                      variant="circle"
                    />
                  </th>
                  <td>{t("share.me")}</td>
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
                      <td>
                        {shareRight.type !== "sharebookmark" && (
                          <Avatar
                            alt={t("explorer.modal.share.avatar.shared.alt")}
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
            {t("share.save.sharebookmark")}
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
                    placeholder={t(
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
                  onClick={() => {
                    saveBookmark(refBookmark.current!.value!);
                  }}
                  className="text-nowrap"
                >
                  {t("explorer.modal.share.sharebookmark.save")}
                </Button>
              </FormControl>
            </div>
          )}
        </div>

        <hr />

        <Heading
          headingStyle="h4"
          level="h3"
          className="mb-16 d-flex align-items-center"
        >
          <div className="me-8">{t("explorer.modal.share.search")}</div>
          <Tooltip
            message={
              "Vos favoris de partage s’affichent en priorité dans votre liste lorsque vous recherchez un groupe ou une personne, vous pouvez les retrouver dans l’annuaire."
            }
            placement="top"
          >
            <InfoCircle className="c-pointer" height="18" />
          </Tooltip>
        </Heading>

        <FormControl className="d-flex align-items-center" id="search">
          <Input
            className="max-w-512"
            noValidationIcon
            placeholder={
              showSearchAdmlHint()
                ? t("explorer.search.adml.hint")
                : t("explorer.modal.share.search.placeholder")
            }
            size="md"
            type="search"
            onChange={handleSearchInputChange}
          />
          {showSearchLoading() && (
            <div className="d-flex align-items-center p-4">
              <Loading isLoading={searchPending} />
              <span className="ps-4">{t("explorer.search.pending")}</span>
            </div>
          )}
          {showSearchNoResults() && (
            <div className="p-4">{t("portal.no.result")}</div>
          )}
        </FormControl>
        {searchResults?.length > 0 && (
          <div className="position-absolute w-100 max-w-512 z-1 bg-white shadow rounded-4 d-block show py-12 px-8">
            <SelectList
              options={searchResults}
              hideCheckbox={true}
              isMonoSelection={true}
              onChange={handleSearchResultsChange}
            ></SelectList>
          </div>
        )}
        <ShareResourceModalFooter
          radioPublicationValue={radioPublicationValue}
          onRadioPublicationChange={handleRadioPublicationChange}
        />
      </Modal.Body>
      <Modal.Footer>
        <Button
          type="button"
          color="tertiary"
          variant="ghost"
          onClick={onCancel}
        >
          {t("explorer.cancel")}
        </Button>

        <Button
          type="button"
          color="primary"
          variant="filled"
          onClick={handleShare}
          disabled={!canSave()}
        >
          {t("share")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
