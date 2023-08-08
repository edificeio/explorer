import {
  type ChangeEvent,
  useCallback,
  useEffect,
  useId,
  useState,
} from "react";

import { Bookmark } from "@edifice-ui/icons";
import {
  Alert,
  type OptionListItemType,
  useIsAdml,
  useDebounce,
  useHotToast,
  useOdeClient,
  useUser,
} from "@edifice-ui/react";
import {
  odeServices,
  type ShareRightAction,
  type ShareRightWithVisibles,
  type ShareSubject,
  type ShareRight,
  type ShareRightActionDisplayName,
  type BlogUpdate,
} from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import { useShareResource, useUpdateResource } from "~/services/queries";
import { useSelectedResources } from "~/store";

interface useShareResourceModalProps {
  onSuccess: () => void;
  onCancel: () => void;
  payloadUpdatePublishType: BlogUpdate;
}

export default function useShareResourceModal({
  onSuccess,
  payloadUpdatePublishType,
}: useShareResourceModalProps) {
  const { appCode } = useOdeClient();
  const { t } = useTranslation();
  const { user, avatar } = useUser();
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
  const debouncedSearchInputValue = useDebounce<string>(searchInputValue, 500);
  const [searchAPIResults, setSearchAPIResults] = useState<ShareSubject[]>([]);
  const [searchResults, setSearchResults] = useState<OptionListItemType[]>([]);
  const [showBookmarkMembers, setShowBookmarkMembers] =
    useState<boolean>(false);
  const [searchPending, setSearchPending] = useState<boolean>(false);

  const { isAdml } = useIsAdml();

  const selectedResources = useSelectedResources();
  const updateResource = useUpdateResource();
  const shareResource = useShareResource();

  const { hotToast } = useHotToast(Alert);

  useEffect(() => {
    initShareRightsAndActions();
  }, []);

  useEffect(() => {
    search(debouncedSearchInputValue);
  }, [debouncedSearchInputValue]);

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
      .getRightsForResource(appCode, selectedResources[0]?.assetId);

    setShareRights(rights);
  }, []);

  const handleActionCheckbox = (
    shareRight: ShareRight,
    actionName: ShareRightActionDisplayName,
  ) => {
    setShareRights(({ rights, ...props }: ShareRightWithVisibles) => {
      const newShareRights: ShareRight[] = [...rights];
      const index: number = newShareRights.findIndex(
        (x) => x.id === shareRight.id,
      );
      const actionObject = shareRightActions.filter(
        (a) => a.id === actionName,
      )[0];

      const isActionRemoving: boolean =
        newShareRights[index].actions.findIndex(
          (action) => action.id === actionName,
        ) > -1;

      if (isActionRemoving) {
        // remove selected action and actions that requires the selected action
        let updatedActions = newShareRights[index].actions.filter(
          (action) => action.id !== actionName,
        );
        const requiredActions = shareRightActions.filter(
          (action) => action.requires?.includes(actionName),
        );
        updatedActions = updatedActions.filter(
          (action) =>
            !requiredActions.find(
              (requiredAction) => requiredAction.id === action.id,
            ),
        );

        newShareRights[index] = {
          ...newShareRights[index],
          actions: updatedActions,
        };
      } else {
        // add required actions
        const requiredActions = shareRightActions.filter(
          (shareRightAction) =>
            actionObject.requires?.includes(shareRightAction.id) &&
            !newShareRights[index].actions.find(
              (action) => action.id === shareRightAction.id,
            ),
        );
        newShareRights[index] = {
          ...newShareRights[index],
          actions: [
            ...newShareRights[index].actions,
            actionObject,
            ...requiredActions,
          ],
        };
      }

      // if bookmark then apply right to users and groups
      if (shareRight.type === "sharebookmark") {
        newShareRights[index].users?.forEach((user: { id: any }) => {
          const userIndex = newShareRights.findIndex(
            (item) => item.id === user.id,
          );
          newShareRights[userIndex] = {
            ...newShareRights[userIndex],
            actions: newShareRights[index].actions,
          };
        });

        newShareRights[index].groups?.forEach((user: { id: any }) => {
          const userIndex = newShareRights.findIndex(
            (item) => item.id === user.id,
          );
          newShareRights[userIndex] = {
            ...newShareRights[userIndex],
            actions: newShareRights[index].actions,
          };
        });
      }

      return {
        rights: newShareRights,
        ...props,
      };
    });
  };

  const handleShare = async () => {
    try {
      await updateResource.mutateAsync(payloadUpdatePublishType);
      await shareResource.mutateAsync({
        entId: selectedResources[0]?.assetId,
        shares: shareRights.rights,
      });

      hotToast.success(t("explorer.shared.status.saved"));
      onSuccess?.();
    } catch (e) {
      console.error("Failed to save share", e);
      hotToast.error(t("explorer.shared.status.error"));
    }
  };

  const handleDeleteRow = (shareRight: ShareRight) => {
    setShareRights((state: any) => {
      return {
        ...state,
        rights: shareRights.rights.filter(
          (right: { id: any }) =>
            right.id !== shareRight.id &&
            !shareRight.users?.find(
              (user: { id: any }) => user.id === right.id,
            ) &&
            !shareRight.groups?.find(
              (group: { id: any }) => group.id === right.id,
            ),
        ),
      };
    });
  };

  const handleSearchInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSearchInputValue(event.target.value);
  };

  const showSearchNoResults = (): boolean => {
    return (
      (!searchPending &&
        !isAdml &&
        debouncedSearchInputValue.length > 0 &&
        searchResults.length === 0) ||
      (!searchPending &&
        isAdml &&
        debouncedSearchInputValue.length > 3 &&
        searchResults.length === 0)
    );
  };

  const showSearchAdmlHint = (): boolean => {
    return isAdml && searchInputValue.length < 3;
  };

  const showSearchLoading = (): boolean => {
    return searchPending && searchInputValue.length > 0;
  };

  const search = async (searchInputValue: string) => {
    setSearchPending(true);
    // start search from 1 caracter length for non Adml but start from 3 for Adml
    if (
      (!isAdml && searchInputValue.length >= 1) ||
      (isAdml && searchInputValue.length >= 3)
    ) {
      const resSearchShareSubjects = await odeServices
        .share()
        .searchShareSubjects(
          appCode,
          selectedResources[0]?.assetId,
          searchInputValue,
        );
      setSearchAPIResults(resSearchShareSubjects);

      const adaptedResults = resSearchShareSubjects
        // exclude subjects that are already in the share table
        .filter(
          (right: { id: any }) =>
            !shareRights.rights.find(
              (shareRight: { id: any }) => shareRight.id === right.id,
            ),
        )
        // exclude owner from results
        .filter(
          (right: { type: string; id: any }) =>
            !(
              right.type === "user" &&
              right.id === selectedResources[0].creatorId
            ),
        )
        .map((searchResult: { id: any; displayName: any; type: string }) => {
          return {
            value: searchResult.id,
            label: searchResult.displayName,
            icon: searchResult.type === "sharebookmark" ? Bookmark : null,
          };
        });

      setSearchResults(adaptedResults);
    } else {
      setSearchResults([]);
      Promise.resolve();
    }

    setSearchPending(false);
  };

  const handleSearchResultsChange = async (model: Array<string | number>) => {
    const shareSubject = searchAPIResults.find(
      (searchAPIResult) => searchAPIResult.id === model[0],
    );

    const defaultActions: ShareRightAction[] = [
      {
        id: "read",
        displayName: "read",
      },
      {
        id: "comment",
        displayName: "comment",
      },
    ];

    if (shareSubject) {
      let rightsToAdd: ShareRight[] = [];

      if (shareSubject.type === "sharebookmark") {
        const bookmarkRes = await odeServices
          .directory()
          .getBookMarkById(shareSubject.id);

        rightsToAdd.push({
          ...bookmarkRes,
          type: "sharebookmark",
          avatarUrl: "",
          directoryUrl: "",
          actions: defaultActions,
        });

        bookmarkRes?.users
          .filter(
            (user: { id: any }) =>
              !shareRights.rights.find(
                (right: { id: any }) => right.id === user.id,
              ),
          )
          .forEach((user: any) => {
            rightsToAdd.push({
              ...user,
              type: "user",
              avatarUrl: "",
              directoryUrl: "",
              actions: defaultActions,
              isBookmarkMember: true,
            });
          });
        bookmarkRes.groups
          .filter(
            (group: { id: any }) =>
              !shareRights.rights.find(
                (right: { id: any }) => right.id === group.id,
              ),
          )
          .forEach((group: any) => {
            rightsToAdd.push({
              ...group,
              type: "group",
              avatarUrl: "",
              directoryUrl: "",
              actions: defaultActions,
              isBookmarkMember: true,
            });
          });
      } else {
        rightsToAdd = [
          {
            ...shareSubject,
            actions: [
              {
                id: "read",
                displayName: "read",
              },
              {
                id: "comment",
                displayName: "comment",
              },
            ],
          },
        ];
      }

      setShareRights({
        ...shareRights,
        rights: [...shareRights.rights, ...rightsToAdd],
      });
      setSearchResults(searchResults.filter((s) => s.value !== model[0]));
    }
  };

  const hasRight = (
    shareRight: ShareRight,
    shareAction: ShareRightAction,
  ): boolean => {
    return (
      shareRight.actions.filter((a: { id: any }) => shareAction.id === a.id)
        .length > 0
    );
  };
  const currentIsAuthor = (): boolean => {
    for (const res of selectedResources) {
      if (res.creatorId !== user?.userId) {
        return false;
      }
    }
    return true;
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
      hotToast.error(t("explorer.bookmarked.status.error"));
    }
  };

  const handleBookmarkMembersToggle = () => {
    setShowBookmarkMembers(!showBookmarkMembers);
  };

  const showShareRightLine = (shareRight: ShareRight): boolean =>
    (shareRight.isBookmarkMember && showBookmarkMembers) ||
    !shareRight.isBookmarkMember;

  return {
    currentIsAuthor,
    idBookmark,
    myAvatar: avatar,
    shareRights,
    shareRightActions,
    showBookmarkInput,
    searchInputValue,
    searchResults,
    bookmarkName,
    showBookmarkMembers,
    debouncedSearchInputValue,
    searchPending,
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
  };
}
