import { useRef } from "react";

import {
  Bookmark,
  Close,
  InfoCircle,
  RafterDown,
  Save,
} from "@edifice-ui/icons";
import {
  Avatar,
  Button,
  Checkbox,
  Combobox,
  FormControl,
  Heading,
  IconButton,
  Modal,
  Tooltip,
  VisuallyHidden,
  useOdeClient,
} from "@edifice-ui/react";
import { UseMutationResult } from "@tanstack/react-query";
import {
  IResource,
  PutShareResponse,
  ShareRight,
  UpdateParameters,
  UpdateResult,
} from "edifice-ts-client";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";

import Blog from "./apps/Blog";
import { useSearch } from "./hooks/useSearch";
import useShare from "./hooks/useShare";
import useShareBlog from "./hooks/useShareBlog";
import { useShareBookmark } from "./hooks/useShareBookmark";
import { hasRight } from "./utils/hasRight";
import { showShareRightLine } from "./utils/showShareRightLine";

interface ShareResourceModalProps {
  isOpen: boolean;
  resource: IResource;
  updateResource: UseMutationResult<
    UpdateResult,
    unknown,
    UpdateParameters,
    unknown
  >;
  shareResource: UseMutationResult<
    PutShareResponse,
    unknown,
    {
      resourceId: string;
      rights: ShareRight[];
    },
    unknown
  >;
  onSuccess: () => void;
  onCancel: () => void;
}

export default function ShareResourceModal({
  isOpen,
  resource,
  updateResource,
  shareResource,
  onSuccess,
  onCancel,
}: ShareResourceModalProps) {
  const { appCode } = useOdeClient();
  const {
    radioPublicationValue,
    shareBlogPayload,
    handleRadioPublicationChange,
  } = useShareBlog({ resource });

  const {
    state: { isSharing, shareRights, shareRightActions },
    dispatch: shareDispatch,
    myAvatar,
    currentIsAuthor,
    handleShare,
    toggleRight,
    handleDeleteRow,
  } = useShare({
    resource,
    updateResource,
    shareResource,
    onSuccess,
  });

  const {
    state: { searchResults, searchInputValue },
    showSearchAdmlHint,
    showSearchLoading,
    showSearchNoResults,
    handleSearchInputChange,
    handleSearchResultsChange,
  } = useSearch({ resource, shareRights, shareDispatch });

  const refBookmark = useRef<HTMLInputElement>(null);

  const {
    showBookmark,
    toggleBookmark,
    bookmark,
    setBookmark,
    saveBookmark,
    showBookmarkInput,
    toggleBookmarkInput,
  } = useShareBookmark({ shareRights, shareDispatch });

  const { t } = useTranslation();

  const searchPlaceholder = showSearchAdmlHint()
    ? t("explorer.search.adml.hint")
    : t("explorer.modal.share.search.placeholder");

  return createPortal(
    <Modal id="share_modal" size="lg" isOpen={isOpen} onModalClose={onCancel}>
      <Modal.Header onModalClose={onCancel}>{t("share.title")}</Modal.Header>
      <Modal.Body>
        <Heading headingStyle="h4" level="h3" className="mb-16">
          {t("explorer.modal.share.usersWithAccess")}
        </Heading>
        <div className="table-responsive">
          <table className="table border align-middle mb-0">
            <thead className="bg-secondary">
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
                    className="text-center text-white"
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
                      className="text-center text-white"
                    >
                      <Checkbox checked={true} disabled />
                    </td>
                  ))}
                  <td></td>
                </tr>
              )}
              {shareRights?.rights.map((shareRight: ShareRight) => {
                return (
                  showShareRightLine(shareRight, showBookmark) && (
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
                                    rotate: showBookmark ? "-180deg" : "0deg",
                                  }}
                                />
                              }
                              type="button"
                              variant="ghost"
                              className="fw-normal ps-0"
                              onClick={toggleBookmark}
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
                          className="text-center text-white"
                        >
                          <Checkbox
                            checked={hasRight(shareRight, shareRightAction)}
                            onChange={() =>
                              toggleRight(shareRight, shareRightAction.id)
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
                    key={bookmark.id}
                    ref={refBookmark}
                    onChange={() => {
                      setBookmark((prev) => ({
                        ...prev,
                        name: refBookmark.current?.value || "",
                      }));
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
                  disabled={bookmark.name.length === 0}
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
        <div className="row">
          <div className="col-10">
            <Combobox
              value={searchInputValue}
              placeholder={searchPlaceholder}
              isLoading={showSearchLoading()}
              noResult={showSearchNoResults()}
              options={searchResults}
              onSearchInputChange={handleSearchInputChange}
              onSearchResultsChange={handleSearchResultsChange}
            />
          </div>
        </div>
        {appCode === "blog" && (
          <Blog
            radioPublicationValue={radioPublicationValue}
            onRadioPublicationChange={handleRadioPublicationChange}
          />
        )}
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
          isLoading={isSharing}
          onClick={() => handleShare(shareBlogPayload)}
          disabled={isSharing}
        >
          {t("share")}
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById("portal") as HTMLElement,
  );
}
