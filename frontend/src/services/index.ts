import { getAppParams } from "@shared/utils/getAppParams";
import { type CreateFolderParameters, odeServices } from "ode-ts-client";

const params = getAppParams();

/* Create Context */
export const createContext = async ({ searchParams }: any) => {
  return await odeServices.resource(params.app, ...params.types).createContext({
    ...searchParams,
    app: params.app,
    types: params.types,
  });
};

/* Create Folder */
export async function createFolder({ searchParams, name, parentId }: any) {
  const parameters: CreateFolderParameters = {
    name,
    parentId,
    app: params.app,
    type: params.types[0],
  };
  return await odeServices.resource(searchParams.app).createFolder(parameters);
}

export const searchContext = async (searchParams: any) => {
  return await odeServices.resource(params.app).searchContext(searchParams);
};

/* Get Folders */
export async function getFolders({ searchParams }: any) {
  const {
    filters: { folder },
  } = searchParams;

  return await odeServices.resource(searchParams.app).searchContext({
    ...searchParams,
    filters: {
      folder,
    },
  });
}

export async function hasRights(actionRights: string[]) {
  return await odeServices.rights().sessionHasWorkflowRights(actionRights);
}
