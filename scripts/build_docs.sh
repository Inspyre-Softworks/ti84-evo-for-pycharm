#!/bin/sh

set -eu

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd "$script_dir/.." && pwd)
docs_dir="$repo_root/docs"
requirements_file="$docs_dir/requirements.txt"
venv_dir="$repo_root/.venv-docs"
venv_python="$venv_dir/bin/python"
requirements_stamp="$venv_dir/.requirements.sha256"
output_dir="$docs_dir/_build/html"
open_after_build=false

case "${1-}" in
    "") ;;
    --open) open_after_build=true ;;
    -h|--help)
        echo "Usage: sh scripts/build_docs.sh [--open]"
        exit 0
        ;;
    *)
        echo "Unknown argument: $1" >&2
        echo "Usage: sh scripts/build_docs.sh [--open]" >&2
        exit 2
        ;;
esac

run_as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        echo "Administrative access is required to install system dependencies." >&2
        exit 1
    fi
}

package_manager() {
    for manager in apt-get dnf yum pacman zypper apk brew pkg; do
        if command -v "$manager" >/dev/null 2>&1; then
            echo "$manager"
            return
        fi
    done
    return 1
}

install_python() {
    manager=$(package_manager || true)
    echo "Python 3.10+ was not found; installing it with ${manager:-the system package manager}..."
    case "$manager" in
        apt-get) run_as_root apt-get update; run_as_root apt-get install -y python3 python3-venv ;;
        dnf) run_as_root dnf install -y python3 ;;
        yum) run_as_root yum install -y python3 ;;
        pacman) run_as_root pacman -Sy --needed --noconfirm python ;;
        zypper) run_as_root zypper --non-interactive install python3 ;;
        apk) run_as_root apk add python3 py3-pip ;;
        brew) brew install python ;;
        pkg) run_as_root pkg install -y python3 ;;
        *)
            echo "Install Python 3.10 or newer, then rerun this script." >&2
            exit 1
            ;;
    esac
}

install_venv_support() {
    manager=$(package_manager || true)
    case "$manager" in
        apt-get) run_as_root apt-get update; run_as_root apt-get install -y python3-venv ;;
        dnf) run_as_root dnf install -y python3 ;;
        yum) run_as_root yum install -y python3 ;;
        pacman) run_as_root pacman -Sy --needed --noconfirm python ;;
        zypper) run_as_root zypper --non-interactive install python3 ;;
        apk) run_as_root apk add python3 py3-pip ;;
        brew) brew install python ;;
        pkg) run_as_root pkg install -y python3 ;;
        *)
            echo "Python's venv module is required. Install it, then rerun this script." >&2
            exit 1
            ;;
    esac
}

install_graphviz() {
    manager=$(package_manager || true)
    echo "Graphviz was not found; installing it with ${manager:-the system package manager}..."
    case "$manager" in
        apt-get) run_as_root apt-get update; run_as_root apt-get install -y graphviz ;;
        dnf) run_as_root dnf install -y graphviz ;;
        yum) run_as_root yum install -y graphviz ;;
        pacman) run_as_root pacman -Sy --needed --noconfirm graphviz ;;
        zypper) run_as_root zypper --non-interactive install graphviz ;;
        apk) run_as_root apk add graphviz ;;
        brew) brew install graphviz ;;
        pkg) run_as_root pkg install -y graphviz ;;
        *)
            echo "Install Graphviz from https://graphviz.org/download/, then rerun this script." >&2
            exit 1
            ;;
    esac
}

find_python() {
    for candidate in python3 python; do
        if command -v "$candidate" >/dev/null 2>&1 &&
            "$candidate" -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)' >/dev/null 2>&1; then
            echo "$candidate"
            return
        fi
    done
    return 1
}

python_command=$(find_python || true)
if [ -z "$python_command" ]; then
    install_python
    python_command=$(find_python || true)
fi
if [ -z "$python_command" ]; then
    echo "Python was installed, but Python 3.10+ is not visible in this shell." >&2
    exit 1
fi

if ! command -v dot >/dev/null 2>&1; then
    install_graphviz
fi
if ! command -v dot >/dev/null 2>&1; then
    echo "Graphviz was installed, but 'dot' is not visible in this shell." >&2
    exit 1
fi

if [ ! -x "$venv_python" ]; then
    echo "Creating documentation environment in .venv-docs..."
    if ! "$python_command" -m venv "$venv_dir"; then
        install_venv_support
        "$python_command" -m venv "$venv_dir"
    fi
fi

requirements_hash=$(
    "$venv_python" -c \
        'import hashlib, sys; print(hashlib.sha256(open(sys.argv[1], "rb").read()).hexdigest())' \
        "$requirements_file"
)
installed_hash=""
if [ -f "$requirements_stamp" ]; then
    installed_hash=$(tr -d '\r\n' < "$requirements_stamp")
fi

dependencies_healthy=false
if "$venv_python" -c 'import importlib.util; modules = ("furo", "hoverxref", "sphinx", "sphinx_copybutton", "sphinx_design", "sphinxext.opengraph"); raise SystemExit(0 if all(importlib.util.find_spec(module) for module in modules) else 1)' >/dev/null 2>&1 &&
    "$venv_python" -m pip check >/dev/null 2>&1; then
    dependencies_healthy=true
fi

if [ "$dependencies_healthy" != true ] || [ "$installed_hash" != "$requirements_hash" ]; then
    echo "Installing documentation dependencies..."
    while IFS= read -r requirement; do
        case "$requirement" in
            \#*|"") ;;
            *" @ http://"*|*" @ https://"*)
                "$venv_python" -m pip install --disable-pip-version-check \
                    --force-reinstall --no-deps "$requirement"
                ;;
        esac
    done < "$requirements_file"
    "$venv_python" -m pip install --disable-pip-version-check --requirement "$requirements_file"
    printf '%s\n' "$requirements_hash" > "$requirements_stamp"
else
    echo "Documentation dependencies are up to date."
fi

rm -rf "$output_dir"

echo "Building documentation..."
"$venv_python" -m sphinx -T -W --keep-going -E -a -b html "$docs_dir" "$output_dir"

index_file="$output_dir/index.html"
echo "Documentation built successfully: $index_file"
if [ "$open_after_build" = true ]; then
    case "$(uname -s)" in
        Darwin) open "$index_file" ;;
        *)
            if command -v xdg-open >/dev/null 2>&1; then
                xdg-open "$index_file"
            else
                echo "No desktop opener was found; open $index_file in a browser."
            fi
            ;;
    esac
fi
