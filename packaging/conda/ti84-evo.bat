@echo off
setlocal
chcp 65001 >nul
set "JAR=%~dp0..\share\ti84-evo\ti84-evo-cli.jar"
if not exist "%JAR%" (
    echo Missing %JAR%. Reinstall the ti84-evo-cli Conda package. 1>&2
    exit /b 1
)
java --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*
exit /b %ERRORLEVEL%
