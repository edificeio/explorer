import requests
import time
from datetime import datetime, timedelta, date
import argparse
import logging


def make_request(app: str, resource: str, date, auth: str, root_url: str, days_delta:int ) -> bool:
    authenticated = True
    from_date = date.strftime("%H%M-%d%m%Y")
    to_date = (date + timedelta(days=days_delta)).strftime("%H%M-%d%m%Y")
    url = f"{root_url}/explorer/reindex/{app}/{resource}?include_folders=true&drop=false&from={from_date}&to={to_date}"
    headers = {'Cookie': f'oneSessionId={auth}'}
    
    start =  int(round(time.time() * 1000))

    response = requests.get(url, headers=headers, timeout=60, allow_redirects=False)

    duration = int(round(time.time() * 1000)) - start
    if response.status_code == 200:
        logging.info(f"{resource}@{app} - {duration} - Request for {date} was successful.")
    elif response.status_code == 302:
        logging.info(f"{resource}@{app} - The script is not authenticated anymore.")
        authenticated = False
    else:
        logging.warning(f"{resource}@{app} - {duration} - Request for {date} failed with status code {response.status_code}.")
    
    return authenticated

def main():
    parser = argparse.ArgumentParser(description="HTTP requests with date parameter")
    parser.add_argument("--start", help="Specify the 'from' date (yyyy-MM-dd)", required=True)
    parser.add_argument("--auth", help="Specify the 'auth' parameter", required=True)
    parser.add_argument("--url", help="Specify the 'url' parameter", required=True)
    parser.add_argument("--apps", help="Specify the apps to reindex as a comma-separated list (default: 'all')", default='all')
    parser.add_argument("--step", help="Specify the number of days to reindex by batch (default: 7)", default=7)
    parser.add_argument("--debug", help="Activate debug (default: 'false')", default='false')

    args = parser.parse_args()
    debug = args.debug in ['true', 'True', 'y', 'Y']
    if debug:
        print(args)
        level = logging.DEBUG
    else:
        level = logging.INFO
    logging.basicConfig(encoding='utf-8', level=level)
    days_delta = int(args.step)
    auth = args.auth
    root_url = args.url
    arg_apps = [app.strip() for app in args.apps.split(',')]
    apps_config = {
        "blog": "blog",
        "exercizer": "subject",
        "mindmap": "mindmap"
    }

    apps_to_reindex = {k: v for k, v in apps_config.items() if k in arg_apps or args.apps == 'all'}

    logging.debug(f"Apps to reindex are {apps_to_reindex}")

    try:
        from_date = datetime.strptime(args.start, "%Y-%m-%d")
    except ValueError:
        logging.error("Invalid date format. Please use yyyy-MM-dd.")
        return

    today = date.today()

    while from_date.date() <= today:
        for (app, resource) in apps_to_reindex.items():
            logging.debug(f"Start reindex {app}/{resource}")
            still_authenticated = make_request(app, resource, from_date, auth, root_url, days_delta)
            if not still_authenticated:
                logging.error("Stopping reindex because the script lost its credentials")
                exit(-1)
        from_date += timedelta(days=days_delta)
    exit(0)

if __name__ == "__main__":
    main()
