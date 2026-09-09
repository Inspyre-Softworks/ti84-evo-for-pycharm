[CmdletBinding()]
param(
    [switch]$Open
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$DocsDir = Join-Path $RepoRoot "docs"
$RequirementsFile = Join-Path $DocsDir "requirements.txt"
$VenvDir = Join-Path $RepoRoot ".venv-docs"
$VenvPython = Join-Path $VenvDir "Scripts\python.exe"
$RequirementsStamp = Join-Path $VenvDir ".requirements.sha256"
$OutputDir = Join-Path $DocsDir "_build\html"

function Find-Python {
    $candidates = @(
        @{ Command = "py"; Arguments = @("-3") },
        @{ Command = "python"; Arguments = @() },
        @{ Command = "python3"; Arguments = @() },
        @{ Command = "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe"; Arguments = @() }
    )

    foreach ($candidate in $candidates) {
        if (-not (Get-Command $candidate.Command -ErrorAction SilentlyContinue)) {
            continue
        }

        & $candidate.Command @($candidate.Arguments) -c `
            "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" `
            2>$null
        if ($LASTEXITCODE -eq 0) {
            return $candidate
        }
    }

    return $null
}

function Install-Python {
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "Python 3.10+ was not found; installing Python 3.12 with winget..."
        & winget install --id Python.Python.3.12 --exact `
            --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) {
            throw "winget could not install Python."
        }
        return
    }

    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-Host "Python 3.10+ was not found; installing Python with Chocolatey..."
        & choco install python312 -y
        if ($LASTEXITCODE -ne 0) {
            throw "Chocolatey could not install Python."
        }
        return
    }

    throw "Python 3.10+ is required. Install it from https://www.python.org/downloads/ and rerun this script."
}

function Find-Graphviz {
    $dot = Get-Command dot -ErrorAction SilentlyContinue
    if ($dot) {
        return $dot.Source
    }

    $knownPaths = @(
        "$env:ProgramFiles\Graphviz\bin\dot.exe",
        "${env:ProgramFiles(x86)}\Graphviz\bin\dot.exe",
        "$env:LOCALAPPDATA\Programs\Graphviz\bin\dot.exe"
    )
    foreach ($path in $knownPaths) {
        if (Test-Path -LiteralPath $path) {
            return $path
        }
    }

    return $null
}

function Install-Graphviz {
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "Graphviz was not found; installing it with winget..."
        & winget install --id Graphviz.Graphviz --exact `
            --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) {
            throw "winget could not install Graphviz."
        }
        return
    }

    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-Host "Graphviz was not found; installing it with Chocolatey..."
        & choco install graphviz -y
        if ($LASTEXITCODE -ne 0) {
            throw "Chocolatey could not install Graphviz."
        }
        return
    }

    if (Get-Command scoop -ErrorAction SilentlyContinue) {
        Write-Host "Graphviz was not found; installing it with Scoop..."
        & scoop install graphviz
        if ($LASTEXITCODE -ne 0) {
            throw "Scoop could not install Graphviz."
        }
        return
    }

    throw "Graphviz is required. Install it from https://graphviz.org/download/ and rerun this script."
}

$Python = Find-Python
if (-not $Python) {
    Install-Python
    $Python = Find-Python
}
if (-not $Python) {
    throw "Python was installed, but it is not visible in this terminal. Open a new terminal and rerun the script."
}

$DotPath = Find-Graphviz
if (-not $DotPath) {
    Install-Graphviz
    $DotPath = Find-Graphviz
}
if (-not $DotPath) {
    throw "Graphviz was installed, but dot.exe is not visible. Open a new terminal and rerun the script."
}
$env:PATH = "$(Split-Path $DotPath -Parent);$env:PATH"

if (-not (Test-Path -LiteralPath $VenvPython)) {
    Write-Host "Creating documentation environment in .venv-docs..."
    & $Python.Command @($Python.Arguments) -m venv $VenvDir
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create the documentation virtual environment."
    }
}

$RequirementsHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $RequirementsFile).Hash
$InstalledHash = if (Test-Path -LiteralPath $RequirementsStamp) {
    (Get-Content -Raw -LiteralPath $RequirementsStamp).Trim()
} else {
    ""
}

& $VenvPython -c "import importlib.util; modules = ('furo', 'hoverxref', 'sphinx', 'sphinx_copybutton', 'sphinx_design', 'sphinxext.opengraph'); raise SystemExit(0 if all(importlib.util.find_spec(module) for module in modules) else 1)" 2>$null
$DependenciesHealthy = $LASTEXITCODE -eq 0
if ($DependenciesHealthy) {
    & $VenvPython -m pip check *> $null
    $DependenciesHealthy = $LASTEXITCODE -eq 0
}
if (-not $DependenciesHealthy -or $InstalledHash -ne $RequirementsHash) {
    Write-Host "Installing documentation dependencies..."
    $DirectRequirements = Get-Content -LiteralPath $RequirementsFile |
        Where-Object { $_ -match '^\s*[^#].+\s@\shttps?://' }
    foreach ($DirectRequirement in $DirectRequirements) {
        & $VenvPython -m pip install --disable-pip-version-check `
            --force-reinstall --no-deps $DirectRequirement
        if ($LASTEXITCODE -ne 0) {
            throw "Could not install direct documentation dependency: $DirectRequirement"
        }
    }
    & $VenvPython -m pip install --disable-pip-version-check --requirement $RequirementsFile
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install the documentation dependencies."
    }
    Set-Content -LiteralPath $RequirementsStamp -Value $RequirementsHash -Encoding ascii
} else {
    Write-Host "Documentation dependencies are up to date."
}

if (Test-Path -LiteralPath $OutputDir) {
    Remove-Item -LiteralPath $OutputDir -Recurse -Force
}

Write-Host "Building documentation..."
& $VenvPython -m sphinx -T -W --keep-going -E -a -b html $DocsDir $OutputDir
if ($LASTEXITCODE -ne 0) {
    throw "The documentation build failed."
}

$IndexFile = Join-Path $OutputDir "index.html"
Write-Host "Documentation built successfully: $IndexFile"
if ($Open) {
    Start-Process $IndexFile
}
