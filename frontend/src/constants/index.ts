import { TransportFrameworkFactory } from "edifice-ts-client";

export const { http } = TransportFrameworkFactory.instance();

/**
 * Object literal to find correct app code for Library Webapp
 */
export const libraryMaps: Record<string, string> = {
  blog: "Blog",
  mindmap: "MindMap",
  scrapbook: "ScrapBook",
  collaborativewall: "CollaborativeWall",
  timelinegenerator: "TimelineGenerator",
  wiki: "Wiki",
  exercizer: "Exercizer",
};
