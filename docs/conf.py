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
  "sphinx.ext.graphviz",
  "sphinx.ext.autosectionlabel",
  "sphinx_design",
  "sphinx_copybutton",
  "hoverxref.extension",
  "sphinxext.opengraph",
]

graphviz_output_format = 'svg'
autosectionlabel_prefix_document = True
copybutton_exclude = '.linenos, .gp'
hoverxref_auto_ref = True
hoverxref_roles = ['term']
hoverxref_role_types = {'ref': 'tooltip', 'term': 'tooltip'}

exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

html_theme = "furo"
html_theme_options = {
    'navigation_with_keys': True,

    'light_css_variables': {
        'color-brand-primary': '#269745',
        'color-brand-content': '#269745',
        'color-glossary-term': '#a161f0',
        'color-highlight-on-target': 'rgba(38, 151, 69, 0.18)',
        'color-accent-soft': 'rgba(38, 151, 69, 0.08)',
        'color-accent-border': 'rgba(38, 151, 69, 0.38)',
        'color-selection-background': 'rgba(38, 151, 69, 0.32)',
    },

    'dark_css_variables': {
        'color-brand-primary': '#a161f0',
        'color-brand-content': '#a161f0',
        'color-glossary-term': '#269745',
        'color-highlight-on-target': 'rgba(161, 97, 240, 0.25)',
        'color-accent-soft': 'rgba(161, 97, 240, 0.10)',
        'color-accent-border': 'rgba(161, 97, 240, 0.42)',
        'color-selection-background': 'rgba(161, 97, 240, 0.38)',
    },
}
html_title = project


html_static_path = ['_static']
html_css_files = ['custom.css']
