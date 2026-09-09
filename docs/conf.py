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
  "sphinx.ext.graphviz"
]

graphviz_output_format = 'svg'

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

html_theme = "furo"
html_theme_options = {
    'navigation_with_keys': True,

    'light_css_variables': {
        'color-brand-primary': '#5e5ce6',
        'color-brand-content': '#5e5ce6',
    },

    'dark_css_variables': {
        'color-brand-primary': '#a161f0',
        'color-brand-content': '#a161f0',
    },
}
html_title = project


html_static_path = ['_static']
html_css_files = ['custom.css']
