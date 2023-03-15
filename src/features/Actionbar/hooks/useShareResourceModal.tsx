import {
  type ChangeEvent,
  useCallback,
  useEffect,
  useId,
  useState,
  type KeyboardEvent,
} from "react";

import { Alert, useOdeClient } from "@ode-react-ui/core";
import { useHotToast } from "@ode-react-ui/hooks";
import useExplorerStore from "@store/index";
import {
  odeServices,
  type ShareRight,
  type ShareRightAction,
  type ShareSubject,
} from "ode-ts-client";
import {
  type ShareRightActionDisplayName,
  type ShareRightWithVisibles,
} from "ode-ts-client/dist/services/ShareService";

interface useShareResourceModalProps {
  onSuccess: () => void;
  onCancel: () => void;
}

export default function useShareResourceModal({
  onSuccess,
  onCancel,
}: useShareResourceModalProps) {
  const { session } = useOdeClient();
  const [idBookmark, setIdBookmark] = useState<string>(useId());
  const [shareRights, setShareRights] = useState<ShareRightWithVisibles>({
    rights: [],
    visibleBookmarks: [],
    visibleGroups: [],
    visibleUsers: [],
  });
  const [shareRightActions, setShareRightActions] = useState<
    ShareRightAction[]
  >([]);

  const [bookmarkName, setBookmarkName] = useState("");
  const [showBookmarkInput, toggleBookmarkInput] = useState<boolean>(false);
  const [searchInputValue, setSearchInputValue] = useState<string>("");
  const [searchResults, setSearchResults] = useState<ShareSubject[]>([]);

  const getSelectedIResources = useExplorerStore(
    (state) => state.getSelectedIResources,
  );
  const shareResource = useExplorerStore((state) => state.shareResource);

  const { appCode } = useOdeClient();

  useEffect(() => {
    initShareRightsAndActions();
  }, []);

  /**
   * Init share data for the sharing table:
   * 1/ Initialize actions for the sharing table header
   * 2/ Initialize shareRights (users/groups) for the sharing table rows
   */
  const initShareRightsAndActions = useCallback(async () => {
    const shareRightActions: ShareRightAction[] = await odeServices
      .share()
      .getActionsForApp(appCode);
    setShareRightActions(shareRightActions);

    const rights: ShareRightWithVisibles = await odeServices
      .share()
      .getRightsForResource(appCode, getSelectedIResources()[0]?.assetId);

    setShareRights(rights);
  }, []);

  const handleActionCheckbox = (
    item: { id: string },
    actionName: ShareRightActionDisplayName,
  ) => {
    setShareRights(({ rights, ...props }: ShareRightWithVisibles) => {
      const newItems = [...rights];
      const index = newItems.findIndex((x) => x.id === item.id);
      const findAction = newItems[index].actions.filter(
        (a) => a.id === actionName,
      );
      const actionObject = shareRightActions.filter(
        (a) => a.id === actionName,
      )[0];
      if (findAction.length > 0) {
        // if already has right => keep only lowest rights
        newItems[index] = {
          ...newItems[index],
          actions: [
            ...shareRightActions.filter(
              (a) => (a.priority || 0) < (actionObject.priority || 0),
            ),
          ],
        };
        return {
          rights: newItems,
          ...props,
        };
      } else {
        // if not have right => keep only lowest rights and equals
        newItems[index] = {
          ...newItems[index],
          actions: [
            ...shareRightActions.filter(
              (a) => (a.priority || 0) <= (actionObject.priority || 0),
            ),
          ],
        };
        return {
          rights: newItems,
          ...props,
        };
      }
    });
  };

  const { hotToast } = useHotToast(Alert);
  const handleShare = async () => {
    try {
      await shareResource(
        getSelectedIResources()[0]?.assetId,
        shareRights.rights,
      );
      // TODO i18n
      hotToast.success("Partage sauvegardé");
      onSuccess?.();
    } catch (e) {
      console.error("Failed to save share", e);
      // TODO i18N
      hotToast.error("Erreur lors du partage");
    }
  };

  const handleDeleteRow = (shareRightId: string) => {
    setShareRights((state) => {
      return {
        ...state,
        rights: shareRights.rights.filter(
          (shareRight) => shareRight.id !== shareRightId,
        ),
      };
    });
  };

  const handleSearchInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSearchInputValue(event.target.value);
    search(event.target.value);
  };

  const handleSearchInputKeyUp = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      search(searchInputValue);
    }
  };

  const handleSearchButtonClick = async (
    event: React.MouseEvent<HTMLButtonElement>,
  ) => {
    search(searchInputValue);
  };

  const search = async (searchInputValue: string) => {
    // start from 1 because it is front search
    if (searchInputValue.length >= 1) {
      const service = odeServices.share();
      // eslint-disable-next-line @typescript-eslint/await-thenable
      const response = await service.findUsers(searchInputValue, {
        visibleBookmarks: shareRights.visibleBookmarks,
        visibleUsers: shareRights.visibleUsers,
        visibleGroups: shareRights.visibleGroups,
      });
      setSearchResults(
        response.filter(
          (r) =>
            !shareRights.rights.find((shareRight) => shareRight.id === r.id),
        ),
      );
    } else {
      setSearchResults([]);
      Promise.resolve();
    }
  };

  const handleSearchResultClick = (
    event: React.MouseEvent<HTMLLIElement>,
    searchResult: ShareSubject,
  ) => {
    setShareRights({
      ...shareRights,
      rights: [
        ...shareRights.rights,
        {
          ...searchResult,
          actions: [{ id: "read", displayName: "read" }],
        },
      ],
    });
    setSearchResults(searchResults.filter((s) => s.id !== searchResult.id));
  };

  const hasRight = (
    shareRight: ShareRight,
    shareAction: ShareRightAction,
  ): boolean => {
    return shareRight.actions.filter((a) => shareAction.id === a.id).length > 0;
  };

  const canSave = () => {
    // cansave only if non empty rights
    /* FIX : can save event if empty
    return (
      shareRights.rights.filter((right) => {
        return right.actions.length > 0;
      }).length > 0
    );
    */
    return true;
  };

  const saveBookmark = async (name: string) => {
    try {
      const res = await odeServices.directory().saveBookmarks(name, {
        users: shareRights.rights
          .filter((right) => right.type === "user")
          .map((u) => u.id),
        groups: shareRights.rights
          .filter((right) => right.type === "group")
          .map((u) => u.id),
        bookmarks: shareRights.rights
          .filter((right) => right.type === "sharebookmark")
          .map((u) => u.id),
      });
      hotToast.success("Favoris sauvegardé");
      setShareRights((state) => {
        return {
          ...state,
          visibleBookmarks: [
            ...state.visibleBookmarks,
            {
              displayName: name,
              id: res.id,
            },
          ],
        };
      });
      setIdBookmark(idBookmark + new Date().getTime().toString());
      toggleBookmarkInput(false);
    } catch (e) {
      console.error("Failed to save bookmark", e);
      hotToast.error("Erreur lors de la sauvegarde");
    }
  };

  return {
    idBookmark,
    myAvatar: session.avatarUrl,
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
  };
}
