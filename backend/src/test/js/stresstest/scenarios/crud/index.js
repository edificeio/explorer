import http from "k6/http";
import exec from "k6/execution";
import { check, group, sleep, fail } from "k6";
import chai, {
  describe,
  expect,
} from "https://jslib.k6.io/k6chaijs/4.3.4.2/index.js";
import {
  authenticateWeb,
  getConnectedUserId,
  getHeaders,
} from "../../utils/user.utils.js";
import { BASE_URL } from "../../utils/env.utils.js";
import { Session } from "../../utils/authentication.utils.js";
/**
 * Helper to log payload if http failed
 * @param {*} response
 */
export function logErrorPayload(response, title) {
  if (!check(response, { "Status is 200": (r) => r.status === 200 })) {
    console.log(title, response.status, response.body);
  }
}

let USER_WEB_LOGIN = __ENV.USER_WEB_LOGIN;
let USER_WEB_PWD = __ENV.USER_WEB_PWD;

chai.config.logFailures = true;

export let options = {
  vus: __ENV.VUS_COUNT || 1,
  iterations: __ENV.VUS_COUNT || 1,
  thresholds: {
    checks: [
    {
      threshold: "rate > 0",
      abortOnFail: false,
    }],
    // http_req_duration: ['p(95)<500'],
    // error_rate: ['rate<0.1'],
  },
};
const BLOG_NUMBER = __ENV.BLOG_NUMBER || 1;
const SLEEP_SECONDS = __ENV.SLEEP_SECONDS || 1;
console.log(
  "Number of blog to create:",
  BLOG_NUMBER,
  "configured sleep seconds=",
  SLEEP_SECONDS
);
const suffix = new Date().getTime();
let vueId = 0;
function getVueId() {
  return "vu" + vueId++;
}
/**
 * GOAL: Ensure that CRUD operation works fine on explorer API when we have a lot of requests
 * Steps:
 * - Authenticate
 * - Create X blogs having title "blog-${timestamp+i}"
 * - Sleep ${SLEEP_SECONDS}s
 * - Check if ${BLOG_NUMBER} blogs are fetchable through API Get /explorer/resources?id=
 * - Update ${BLOG_NUMBER} blogs changing title to "blog-${timestamp+i}-updated"
 * - Sleep ${SLEEP_SECONDS}s
 * - Check if ${BLOG_NUMBER} blogs are fetchable through API Get /explorer/resources?id=
 * - Check if ${BLOG_NUMBER} blogs have name changed to "blog-${timestamp+i}-updated"
 * - Delete ${BLOG_NUMBER} blogs changing title to "blog-${timestamp+i}-updated"
 * - Sleep ${SLEEP_SECONDS}s
 * - Check if ${BLOG_NUMBER} blogs are not visible anymore through API Get /explorer/resources?id=
 *
 * We expect less than 5% errors
 */
export default function crudScenario() {
  let blogs = {};
  describe("[EXPLORER] crud blogs", () => {
    const vueId = getVueId()
    function blogName(i) {
      return `blog-${vueId}-${suffix + i}`;
    }
    let webUserId;
    let webSession = new Session(null, null, -1);
    group("[EXPLORER] should authenticate", () => {
      if (webSession.isExpired()) {
        webSession = authenticateWeb(USER_WEB_LOGIN, USER_WEB_PWD);
      } else {
        let jar = http.cookieJar();
        jar.set(BASE_URL, "oneSessionId", webSession.token);
        for (let cookie of webSession.cookies) {
          jar.set(BASE_URL, cookie.name, cookie.value);
        }
      }
      let session = webSession;
      if (!webUserId) {
        webUserId = getConnectedUserId(session);
      }
    });
    group("[EXPLORER] should create blog", () => {
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.post(
          `${BASE_URL}/blog`,
          JSON.stringify({
            title: title,
            description: "<div><h1>MY BLOG</h1><p>MY CONTENT</p></div>",
            visibility: "OWNER",
            "comment-type": "IMMEDIATE",
            "publish-type": "RESTRAINT",
            slug: null,
            thumbnail: "",
          }),
          { redirects: 0, headers }
        );
        logErrorPayload(response, title);
        let checksOk = check(response, {
          "check blog created": (r) => r.status == 200,
          "check blog created without error": (r) => !r.json()["error"],
        });
        if (!checksOk) {
          console.error("Could not create blog");
          console.error(response);
          fail("Could not create blog: "+title);
        }
      }
    });
    group("[EXPLORER] should read blog", () => {
      sleep(SLEEP_SECONDS);
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.get(
          `${BASE_URL}/explorer/resources?application=blog&resource_type=blog&search=${title}`,
          { redirects: 0, headers }
        );
        logErrorPayload(response, title);
        let checkOk = check(response, {
          "check read blog": (r) => r.status == 200,
          ["check blog visibility title=" + title]: (r) =>
            r.json().resources.length > 0,
        });
        if (checkOk) {
          blogs[title] = response.json().resources[0];
        } else {
          console.error("Could not read blog");
          console.error(response);
          fail("Could not read blog: "+title);
        }
      }
    });
    group("[EXPLORER] should update blog", () => {
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let blog = blogs[title];
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.put(
          `${BASE_URL}/blog/${blog.assetId}`,
          JSON.stringify({
            title: title + "-update",
            description: "<div><h1>MY BLOG</h1><p>MY CONTENT</p></div>",
            visibility: "OWNER",
            "comment-type": "IMMEDIATE",
            "publish-type": "RESTRAINT",
            slug: null,
            thumbnail: "",
          }),
          {
            redirects: 0,
            headers,
          }
        );
        logErrorPayload(response, title);
        let checkOk = check(response, {
          "check update blog": (r) => r.status == 200,
        });
        if (!checkOk) {
          console.error("Could not update blog");
          console.error(response);
          fail("Could not update blog: "+title);
        }
      }
    });
    group("[EXPLORER] should read updated blog", () => {
      sleep(SLEEP_SECONDS);
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let blog = blogs[title];
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.get(
          `${BASE_URL}/explorer/resources?application=blog&resource_type=blog&owner=true&id=${blog.id}`,
          "",
          { redirects: 0, headers }
        );
        logErrorPayload(response, title);
        let checkOk = check(response, {
          "check read updated blog": (r) => r.status == 200,
          ["check updated blog visibility id:" + blog.id]: (r) =>
            r.json().resources.length > 0,
          ["check updated blog name is updated id:" + blog.id]: (r) =>
            r.json().resources[0].name === title + "-update",
        });
        if (checkOk) {
          blog = response.json().resources[0];
        } else {
          console.error("Could not read after update blog");
          console.error(response);
          fail("Could not read update blog: "+title);
        }
      }
    });
    group("[EXPLORER] should delete blog", () => {
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let blog = blogs[title];
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.del(
          `${BASE_URL}/explorer`,
          JSON.stringify({
            application: "blog",
            folderIds: [],
            resourceIds: [blog.id],
            resourceType: "blog",
          }),
          { redirects: 0, headers }
        );
        logErrorPayload(response, title);
        let checkOk = check(response, {
          "check deleted blog": (r) => r.status == 200,
        });
        if (!checkOk) {
          console.error("Could not delete blog");
          console.error(response);
          fail("Could not delete blog: "+title);
        }
      }
    });
    group("[EXPLORER] should not found deleted blog", () => {
      sleep(SLEEP_SECONDS);
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let title = blogName(i);
        let blog = blogs[title];
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.get(
          `${BASE_URL}/explorer/resources?application=blog&resource_type=blog&owner=true&id=${blog.id}`,
          "",
          { redirects: 0, headers }
        );
        logErrorPayload(response, title);
        let checkOk = check(response, {
          "check delete blog": (r) => r.status == 200,
          ["check deleted blog visibility id=" + blog.id]: (r) =>
            r.json().resources.length == 0,
        });
        if (checkOk) {
          blog = response.json().resources[0];
        } else {
          console.error("Could not fetch after deleted blog");
          console.error(response);
          fail("Error on read blog after delete: "+blog.id);
        }
      }
    });
  });
}
