# Reindexer

# Description

This script allows to reindex EUR content based on :
- the root URL of the platform whose content we want to reindex
- a specified list of application to reindex
- the value of the oneSessionId cookie of the user who triggers the reindexation

# Installation

Requirements : python 3.9.

```shell
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

# Run


```shell
python3 -m venv .venv
source .venv/bin/activate
python3 main.py --start=<START> --auth=<YOUR_COOKIE> --url=<PF_ROOT_URL> --step=<STEP> [--apps=<APPS>]
```

Command arguments :

- `start`, the earliest date (inclusive) of the resources to reindex. The expected format is `yyyy-MM-dd`.
- `auth`, the raw content of the `oneSessionId` cookie o the user to be used for this indexation operation.
- `url`, root url of the platform (e.g. https://recette-ode1.opendigitaleducation.com/)
- `step` (default 1), positive integer number of days to reindex at each iteration (i.e. the script will try to reindex resources n days by n days)
- `apps` (optional), comma-separated list of apps to reindex. All apps will be reindexed if it is left blank. Example : `blog,exercizer`