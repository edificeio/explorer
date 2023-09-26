import http from "k6/http";
import { check, group, fail } from "k6";
import chai, {
  describe,
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
export function logErrorPayload(response) {
  if (!check(response, { "Status is 200": (r) => r.status === 200 })) {
    console.log(response.status, response.body);
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
      },
    ],
    // http_req_duration: ['p(95)<500'],
    // error_rate: ['rate<0.1'],
  },
};
const BLOG_NUMBER = __ENV.BLOG_NUMBER || 1;
console.log("Number of read operations per vus:", BLOG_NUMBER);
/**
 * GOAL: Ensure that read operation works fine on explorer API when we have a lot of requests
 * Steps:
 * - Authenticate
 * - Fetch block through API Get /explorer/resources?id=
 *
 */
export default function crudScenario() {
  describe("[EXPLORER] read blogs", () => {
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
    group("[EXPLORER] should read blog", () => {
      for (let i = 0; i < BLOG_NUMBER; i++) {
        let session = webSession;
        let headers = getHeaders(session);
        let response = http.get(
          `${BASE_URL}/explorer/resources?application=blog&resource_type=blog`,
          { redirects: 0, headers }
        );
        logErrorPayload(response);
        let checkOk = check(response, {
          "check read blog": (r) => r.status == 200,
          ["check list non empty"]: (r) => r.json().resources.length > 0,
        });
        if (!checkOk) {
          console.error("Could not read blog");
          console.error(response);
          fail("Could not read blog");
        }
      }
    });
  });
}
