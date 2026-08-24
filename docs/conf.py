from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

project = "TI-84 Evo for PyCharm"
copyright = "2026, Inspyre-Softworks"
author = "Inspyre-Softworks"
release = (ROOT / "VERSION").read_text(encoding="utf-8").strip()

extensions = ["sphinx.ext.napoleon"]
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
html_theme = "alabaster"
html_title = project
