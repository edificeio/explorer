import { Dispatch, useId, useState } from "react";

import { useHotToast, Alert } from "@edifice-ui/react";
import { ShareRightWithVisibles, odeServices } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { ShareAction } from "./useShare";

export const useShareBookmark = ({
  shareRights,
  shareDispatch,
}: {
  shareRights: ShareRightWithVisibles;

  shareDispatch: Dispatch<ShareAction>;
}) => {
  const { hotToast } = useHotToast(Alert);
  const { t } = useTranslation();

  const [bookmark, setBookmark] = useState({
    name: "",
    id: useId(),
  });
  const [showBookmark, setShowBookmark] = useState<boolean>(false);
  const [showBookmarkInput, toggleBookmarkInput] = useState<boolean>(false);

  const toggleBookmark = () => {
    setShowBookmark((prev) => !prev);
  };

  const saveBookmark = async (name: string) => {
    try {
      const res = await odeServices.directory().saveBookmarks(name, {
        users: shareRights.rights
          .filter((right: { type: string }) => right.type === "user")
          .map((u: { id: any }) => u.id),
        groups: shareRights.rights
          .filter((right: { type: string }) => right.type === "group")
          .map((u: { id: any }) => u.id),
        bookmarks: shareRights.rights
          .filter((right: { type: string }) => right.type === "sharebookmark")
          .map((u: { id: any }) => u.id),
      });
      hotToast.success(t("explorer.bookmarked.status.saved"));

      shareDispatch({
        type: "updateShareRights",
        payload: {
          ...shareRights,
          visibleBookmarks: [
            ...shareRights.visibleBookmarks,
            {
              displayName: name,
              id: res.id,
            },
          ],
        },
      });

      setBookmark((prev) => ({
        ...prev,
        bookmarkId: prev.id + new Date().getTime().toString(),
      }));
      toggleBookmarkInput(false);
    } catch (e) {
      console.error("Failed to save bookmark", e);
      hotToast.error(t("explorer.bookmarked.status.error"));
    }
  };

  return {
    showBookmark,
    showBookmarkInput,
    bookmark,
    setBookmark,
    saveBookmark,
    toggleBookmark,
    toggleBookmarkInput,
  };
};
