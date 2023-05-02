import {
  type ChangeEvent,
  useCallback,
  useEffect,
  useId,
  useState,
  type KeyboardEvent,
} from "react";

import { Alert, useOdeClient } from "@ode-react-ui/core";
import { type OptionListItemType } from "@ode-react-ui/core/dist/Dropdown/SelectListProps";
import { useHotToast } from "@ode-react-ui/hooks";
import { Bookmark } from "@ode-react-ui/icons";
import { useShareResource } from "@services/queries/index";
import { useSelectedResources } from "@store/store";
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
  const [searchAPIResults, setSearchAPIResults] = useState<ShareSubject[]>([]);
  const [searchResults, setSearchResults] = useState<OptionListItemType[]>([]);
  const [showBookmarkMembers, setShowBookmarkMembers] =
    useState<boolean>(false);

  const selectedResources = useSelectedResources();

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
        newShareRights[index].actions.findIndex((a) => a.id === actionName) >
        -1;

      if (isActionRemoving) {
        // remove selected action and actions that requires the selected action
        let updatedActions = newShareRights[index].actions.filter(
          (action) => action.id !== actionName,
        );
        const requiredActions = shareRightActions.filter((action) =>
          action.requires?.includes(actionName),
        );
        updatedActions = updatedActions.filter(
          (action) => !requiredActions.includes(action),
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
        newShareRights[index].users?.forEach((user) => {
          const userIndex = newShareRights.findIndex(
            (item) => item.id === user.id,
          );
          newShareRights[userIndex] = {
            ...newShareRights[userIndex],
            actions: newShareRights[index].actions,
          };
        });

        newShareRights[index].groups?.forEach((user) => {
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

  const shareResource = useShareResource();

  const { hotToast } = useHotToast(Alert);
  const handleShare = async () => {
    try {
      await shareResource.mutate({
        entId: selectedResources[0]?.assetId,
        shares: shareRights.rights,
      });
      // TODO i18n
      hotToast.success("Partage sauvegardé");
      onSuccess?.();
    } catch (e) {
      console.error("Failed to save share", e);
      // TODO i18N
      hotToast.error("Erreur lors du partage");
    }
  };

  const handleDeleteRow = (shareRight: ShareRight) => {
    setShareRights((state) => {
      return {
        ...state,
        rights: shareRights.rights.filter(
          (right) =>
            right.id !== shareRight.id &&
            !shareRight.users?.find((user) => user.id === right.id) &&
            !shareRight.groups?.find((group) => group.id === right.id),
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

      setSearchAPIResults(response);

      const adaptedResults = response
        .filter(
          (r) =>
            !shareRights.rights.find((shareRight) => shareRight.id === r.id),
        )
        // exclude owner from results
        .filter(
          (r) =>
            !(r.type === "user" && r.id === selectedResources[0].creatorId),
        )
        .map((searchResult) => {
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
            (user) => !shareRights.rights.find((right) => right.id === user.id),
          )
          .forEach((user) => {
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
            (group) =>
              !shareRights.rights.find((right) => right.id === group.id),
          )
          .forEach((group) => {
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
    return shareRight.actions.filter((a) => shareAction.id === a.id).length > 0;
  };
  const currentIsAuthor = (): boolean => {
    for (const res of selectedResources) {
      if (res.creatorId !== session.user.userId) {
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

  const handleBookmarkMembersToggle = () => {
    setShowBookmarkMembers(!showBookmarkMembers);
  };

  const showShareRightLine = (shareRight: ShareRight): boolean =>
    (shareRight.isBookmarkMember && showBookmarkMembers) ||
    !shareRight.isBookmarkMember;

  return {
    currentIsAuthor,
    idBookmark,
    myAvatar: session.avatarUrl,
    shareRights,
    shareRightActions,
    showBookmarkInput,
    searchInputValue,
    searchResults,
    bookmarkName,
    showBookmarkMembers,
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
  };
}
