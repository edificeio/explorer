import {
  type CreateFolderParameters,
  odeServices,
  type TrashParameters,
  type ISearchParameters,
  type ID,
  type DeleteParameters,
  type MoveParameters,
  type UpdateFolderParameters,
  type ShareRight,
  type UpdateParameters,
  type IFolder,
  type PublishParameters,
} from "ode-ts-client";

/**
 * searchContext API
 * @param searchParams
 * @returns resources, no trashed folders and pagination
 */
export const searchContext = async (searchParams: any) => {
  const search = await odeServices
    .resource(searchParams.app)
    .searchContext(searchParams);

  return {
    ...search,
    folders: search.folders.filter((folder: IFolder) => !folder.trashed),
  };
};

/**
 * createFolder API
 * @param searchParams, name, parentId
 * @returns a new folder
 */
export const createFolder = async ({
  searchParams,
  name,
  parentId,
}: {
  searchParams: ISearchParameters;
  name: string;
  parentId: ID;
}) => {
  const createFolderParameters: CreateFolderParameters = {
    name,
    parentId,
    app: searchParams.app,
    type: searchParams.types[0],
  };
  return await odeServices
    .resource(searchParams.app)
    .createFolder(createFolderParameters);
};

/**
 * updateFolder API
 * @param searchParams, folderId, parentId, name
 * @returns updated folder
 */
export const updateFolder = async ({
  folderId,
  searchParams,
  parentId,
  name,
}: {
  folderId: ID;
  searchParams: ISearchParameters;
  parentId: ID;
  name: string;
}) => {
  const updateFolderParameters: UpdateFolderParameters = {
    folderId,
    name,
    parentId,
    app: searchParams.app,
    type: searchParams.types[0],
  };
  return await odeServices
    .resource(searchParams.app)
    .updateFolder(updateFolderParameters);
};

/**
 * trashAll API
 * @param searchParams, resourceIds, folderIds
 * @move resources and folders to bin
 */
export const trashAll = async ({
  searchParams,
  resourceIds,
  folderIds,
}: {
  searchParams: ISearchParameters;
  resourceIds: ID[];
  folderIds: ID[];
}) => {
  const trashParameters: Omit<TrashParameters, "trash"> = {
    application: searchParams.app,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices.resource(searchParams.app).trashAll(trashParameters);
};

/**
 * deleteAll API
 * @param searchParams, resourceIds, folderIds
 * @delete folders and resources
 */
export const deleteAll = async ({
  searchParams,
  resourceIds,
  folderIds,
}: {
  searchParams: ISearchParameters;
  resourceIds: ID[];
  folderIds: ID[];
}) => {
  const deleteParameters: DeleteParameters = {
    application: searchParams.app,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices
    .resource(searchParams.app)
    .deleteAll(deleteParameters);
};

/**
 * restoreAll API
 * @param searchParams, resourceIds, folderIds
 * @restore trashed folders and resources
 */
export const restoreAll = async ({
  searchParams,
  resourceIds,
  folderIds,
}: {
  searchParams: ISearchParameters;
  resourceIds: ID[];
  folderIds: ID[];
}) => {
  const trashParameters: Omit<TrashParameters, "trash"> = {
    application: searchParams.app,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices
    .resource(searchParams.app)
    .restoreAll(trashParameters);
};

/**
 * moveToFolder API
 * @param searchParams, resourceIds, folderIds, folderId
 * @returns folders and resources to new folderId location
 */
export const moveToFolder = async ({
  searchParams,
  resourceIds,
  folderId,
  folderIds,
}: {
  searchParams: ISearchParameters;
  folderId: ID;
  resourceIds: ID[];
  folderIds: ID[];
}) => {
  const moveParameters: MoveParameters = {
    application: searchParams.app,
    folderId,
    resourceIds,
    folderIds,
  };

  return await odeServices
    .resource(searchParams.app)
    .moveToFolder(moveParameters);
};

/**
 * shareResource API
 * @param searchParams, entId, shares
 * @returns shared resource
 */
export const shareResource = async ({
  searchParams,
  entId,
  shares,
}: {
  searchParams: ISearchParameters;
  entId: ID;
  shares: ShareRight[];
}) => {
  return await odeServices.share().saveRights(searchParams.app, entId, shares);
};

/**
 * updateResource API
 * @param searchParams, params
 * @returns updated resource
 */
export const updateResource = async ({
  searchParams,
  params,
}: {
  searchParams: ISearchParameters;
  params: UpdateParameters;
}) => {
  return await odeServices.resource(searchParams.app).update(params);
};

/**
 * sessionHasWorkflowRights API
 * @param actionRights
 * @returns check if user has rights
 */
export const sessionHasWorkflowRights = async (actionRights: string[]) => {
  return await odeServices.rights().sessionHasWorkflowRights(actionRights);
};

export const goToResource = ({
  searchParams,
  assetId,
}: {
  searchParams: ISearchParameters;
  assetId: ID;
}) => odeServices.resource(searchParams.app).gotoView(assetId);

export const createResource = ({
  searchParams,
  safeFolderId,
}: {
  searchParams: ISearchParameters;
  safeFolderId: ID | undefined;
}) => odeServices.resource(searchParams.app).gotoForm(safeFolderId);

export const printResource = ({
  searchParams,
  assetId,
}: {
  searchParams: ISearchParameters;
  assetId: ID;
}) => odeServices.resource(searchParams.app).gotoPrint(assetId);

export const publishResource = async ({
  searchParams,
  params,
}: {
  searchParams: ISearchParameters;
  params: PublishParameters;
}) => await odeServices.resource(searchParams.app).publish(params);

/**
 * getPreference API
 * @returns check onboarding trash param
 */
export const getOnboardingTrash = async () => {
  const res = await odeServices
    .conf()
    .getPreference<{ showOnboardingTrash: boolean }>("showOnboardingTrash");
  return res;
};

/**
 * savePreference API
 * @returns set onboarding trash param
 */
export const saveOnboardingTrash = async ({
  onSuccess,
}: {
  onSuccess: () => void;
}) => {
  const result = await odeServices
    .conf()
    .savePreference(
      "showOnboardingTrash",
      JSON.stringify({ showOnboardingTrash: false }),
    );
  onSuccess?.();
  return result;
};