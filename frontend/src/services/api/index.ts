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
  CreateParameters,
  IActionParameters,
  GetContextParameters,
  PublishParameters,
} from "edifice-ts-client";

/**
 * searchContext API
 * @param searchParams
 * @returns resources, no trashed folders and pagination
 */
export const searchContext = async (searchParams: GetContextParameters) => {
  const search = await odeServices
    .resource(searchParams.application)
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
  searchParams: ISearchParameters & IActionParameters;
  name: string;
  parentId: ID;
}) => {
  const createFolderParameters: CreateFolderParameters = {
    name,
    parentId,
    application: searchParams.application,
    type: searchParams.types[0],
  };
  return await odeServices
    .resource(searchParams.application)
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
  searchParams: ISearchParameters & IActionParameters;
  parentId: ID;
  name: string;
}) => {
  const updateFolderParameters: UpdateFolderParameters = {
    folderId,
    name,
    parentId,
    application: searchParams.application,
    type: searchParams.types[0],
  };
  return await odeServices
    .resource(searchParams.application)
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
  useAssetIds,
  folderIds,
}: {
  searchParams: ISearchParameters & IActionParameters;
  resourceIds: ID[];
  useAssetIds: boolean;
  folderIds: ID[];
}) => {
  const trashParameters: Omit<TrashParameters, "trash"> = {
    application: searchParams.application,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices
    .resource(searchParams.application)
    .trashAll(trashParameters, useAssetIds);
};

/**
 * deleteAll API
 * @param searchParams, resourceIds, folderIds
 * @delete folders and resources
 */
export const deleteAll = async ({
  searchParams,
  resourceIds,
  useAssetIds,
  folderIds,
}: {
  searchParams: ISearchParameters & IActionParameters;
  resourceIds: ID[];
  useAssetIds: boolean;
  folderIds: ID[];
}) => {
  const deleteParameters: DeleteParameters = {
    application: searchParams.application,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices
    .resource(searchParams.application)
    .deleteAll(deleteParameters, useAssetIds);
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
  useAssetIds,
}: {
  searchParams: ISearchParameters & IActionParameters;
  resourceIds: ID[];
  useAssetIds: boolean;
  folderIds: ID[];
}) => {
  const trashParameters: Omit<TrashParameters, "trash"> = {
    application: searchParams.application,
    resourceType: searchParams.types[0],
    resourceIds,
    folderIds,
  };
  return await odeServices
    .resource(searchParams.application)
    .restoreAll(trashParameters, useAssetIds);
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
  useAssetIds,
}: {
  searchParams: ISearchParameters & IActionParameters;
  folderId: ID;
  resourceIds: ID[];
  useAssetIds: boolean;
  folderIds: ID[];
}) => {
  const moveParameters: MoveParameters = {
    application: searchParams.application,
    folderId,
    resourceIds,
    folderIds,
  };

  return await odeServices
    .resource(searchParams.application)
    .moveToFolder(moveParameters, useAssetIds);
};

/**
 * shareResource API
 * @param searchParams, entId, shares
 * @returns shared resource
 */
export const shareResource = async ({
  application,
  resourceId,
  rights,
}: {
  application: string;
  resourceId: ID;
  rights: ShareRight[];
}) => {
  return await odeServices.share().saveRights(application, resourceId, rights);
};

/**
 * updateResource API
 * @param searchParams, params
 * @returns updated resource
 */
export const updateResource = async ({
  application,
  params,
}: {
  application: string;
  params: UpdateParameters;
}) => {
  return await odeServices.resource(application).update(params);
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
  searchParams: ISearchParameters & IActionParameters;
  assetId: ID;
}) => {
  const url = odeServices
    .resource(searchParams.application)
    .getViewUrl(assetId);
  window.open(url, "_self");
};

export const createResource = ({
  searchParams,
  params,
}: {
  searchParams: ISearchParameters & IActionParameters;
  params: CreateParameters;
}) => {
  const result = odeServices.resource(searchParams.application).create(params);
  return result;
};

export const printResource = ({
  searchParams,
  assetId,
}: {
  searchParams: ISearchParameters & IActionParameters;
  assetId: ID;
}) => {
  const url = odeServices
    .resource(searchParams.application)
    .getPrintUrl(assetId);
  return window.open(url, "_blank");
};

export const goToCreate = ({
  searchParams,
  folderId,
}: {
  searchParams: ISearchParameters & IActionParameters;
  folderId?: ID;
}) => {
  const url = odeServices
    .resource(searchParams.application)
    .getFormUrl(folderId);
  return window.open(url, "_self");
};

export const goToEdit = ({
  searchParams,
  assetId,
}: {
  searchParams: ISearchParameters & IActionParameters;
  assetId: ID;
}) => {
  const url = odeServices
    .resource(searchParams.application)
    .getEditUrl(assetId);

  return window.open(url, "_self");
};

export const goToExport = ({
  searchParams,
  assetId,
}: {
  searchParams: ISearchParameters & IActionParameters;
  assetId: ID;
}) => {
  const url = odeServices
    .resource(searchParams.application)
    .getExportUrl(assetId);

  return window.open(url, "_self");
};

export const publishResource = async ({
  searchParams,
  params,
}: {
  searchParams: ISearchParameters & IActionParameters;
  params: PublishParameters;
}) => await odeServices.resource(searchParams.application).publish(params);

/**
 * getPreference API
 * @returns check onboarding trash param
 */
export const getOnboardingTrash = async (key: string) => {
  const res = await odeServices
    .conf()
    .getPreference<{ showOnboardingTrash: boolean }>(key);
  return res;
};

/**
 * savePreference API
 * @returns set onboarding trash param
 */
export const saveOnboardingTrash = async (key: string) => {
  const result = await odeServices
    .conf()
    .savePreference(key, JSON.stringify({ showOnboardingTrash: false }));

  return result;
};
