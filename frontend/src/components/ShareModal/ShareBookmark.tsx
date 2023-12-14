import { Dispatch, useRef } from "react";

import { Bookmark, RafterDown, Save } from "@edifice-ui/icons";
import { Button, FormControl } from "@edifice-ui/react";
import { ShareRightWithVisibles } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { ShareAction } from "./hooks/useShare";
import { useShareBookmark } from "./hooks/useShareBookmark";

export const ShareBookmark = ({
  shareRights,
  shareDispatch,
}: {
  shareRights: ShareRightWithVisibles;

  shareDispatch: Dispatch<ShareAction>;
}) => {
  const refBookmark = useRef<HTMLInputElement>(null);

  const { t } = useTranslation();

  const {
    bookmarkName,
    setBookmarkName,
    saveBookmark,
    bookmarkId,
    showBookmarkInput,
    toggleBookmarkInput,
  } = useShareBookmark({ shareRights, shareDispatch });

  return (
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
                key={bookmarkId}
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
  );
};
