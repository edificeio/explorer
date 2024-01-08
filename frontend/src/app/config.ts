// import { App, ResourceType, IFilter, IOrder, IAction } from "edifice-ts-client";

const WORFLOW_ACCESS = "org.entcore.blog.controllers.BlogController|blog";
const WORKFLOW_CREATE = "org.entcore.blog.controllers.BlogController|create";
const WORKFLOW_CREATE_PUBLIC =
  "org.entcore.blog.controllers.BlogController|createPublicBlog";
const WORKFLOW_PUBLISH = "org.entcore.blog.controllers.BlogController|publish";
const WORKFLOW_PRINT = "org.entcore.blog.controllers.BlogController|print";

/* interface AppParams {
  app: App;
  types: ResourceType[];
  filters: IFilter[];
  orders: IOrder[];
  actions: IAction[];
  trashActions: IAction[];
  libraryAppFilter?: string;
} */

export const explorerConfig = {
  app: "blog",
  types: ["blog"],
  filters: [
    { id: "owner", defaultValue: true },
    { id: "public", defaultValue: false },
    { id: "shared", defaultValue: true },
  ],
  orders: [
    { id: "name", defaultValue: "asc", i18n: "explorer.sorts.name" },
    { id: "updatedAt", i18n: "explorer.sorts.updatedat" },
  ],
  actions: [
    {
      id: "open",
      workflow: WORFLOW_ACCESS,
      target: "actionbar",
      right: "read",
    },
    {
      id: "share",
      workflow: WORFLOW_ACCESS,
      target: "actionbar",
      right: "manager",
    },
    {
      id: "edit",
      workflow: WORFLOW_ACCESS,
      target: "actionbar",
      right: "manager",
    },
    {
      id: "create",
      workflow: WORKFLOW_CREATE,
      target: "tree",
    },
    {
      id: "createPublic",
      workflow: WORKFLOW_CREATE_PUBLIC,
      target: "tree",
    },
    {
      id: "move",
      workflow: WORFLOW_ACCESS,
      target: "actionbar",
      right: "read",
    },
    {
      id: "publish",
      workflow: WORKFLOW_PUBLISH,
      target: "actionbar",
      right: "creator",
    },
    {
      id: "print",
      workflow: WORKFLOW_PRINT,
      target: "actionbar",
      right: "read",
    },
    {
      id: "delete",
      workflow: WORFLOW_ACCESS,
      target: "actionbar",
      right: "read",
    },
  ],
  trashActions: [
    {
      id: "restore",
      available: true,
      target: "actionbar",
      workflow: "",
      right: "manager",
    },
    {
      id: "delete",
      available: true,
      target: "actionbar",
      workflow: "",
      right: "manager",
    },
  ],
};
