import json
import os
from pathlib import Path
from sqlalchemy.engine import make_url

# Never run persistence tests against the application's real database.
config = json.loads((Path(__file__).parent / ".local/app-config.json").read_text(encoding="utf-8"))
url = make_url(config["database_url"]).set(database="wifiprevent_test")
os.environ["DATABASE_URL"] = url.render_as_string(hide_password=False)
