$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$jar = Join-Path $PSScriptRoot 'ti84-evo-cli.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing $jar. Keep this script next to ti84-evo-cli.jar."
}
& java -jar $jar @args
exit $LASTEXITCODE
