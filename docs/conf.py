from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

project = "TI-84 Evo for PyCharm"
copyright = "2026, Inspyre-Softworks"
author = "Taylor B. | Inspyre-Softworks"
release = (ROOT / "VERSION").read_text(encoding="utf-8").strip()

extensions = [
  "sphinx.ext.napoleon",
  "sphinx.ext.graphiz"
]

graphviz_output_format = 'svg'

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
html_theme = "alabaster"
html_title = project
