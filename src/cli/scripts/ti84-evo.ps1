$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$jar = Join-Path $PSScriptRoot 'ti84-evo-cli.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Missing $jar. Keep this script next to ti84-evo-cli.jar."
}
$condaJava = Join-Path $PSScriptRoot '..\..\Library\bin\java.exe'
$java = if (Test-Path -LiteralPath $condaJava) { $condaJava } else { 'java' }
& $java --enable-native-access=ALL-UNNAMED -jar $jar @args
exit $LASTEXITCODE
