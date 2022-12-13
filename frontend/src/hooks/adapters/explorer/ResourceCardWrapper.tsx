import { IResource } from "ode-ts-client";

import { Card } from "./types";

export default class ResourceCardWrapper implements Card {
  constructor(private res: IResource) {}

  get id() {
    return this.res.id;
  }

  get name() {
    return this.res.name;
  }

  get thumbnail() {
    return this.res.thumbnail;
  }

  get authorId() {
    return this.res.authorId;
  }

  get authorName() {
    return this.res.authorName;
  }
}
