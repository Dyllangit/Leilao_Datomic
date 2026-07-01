@echo off
REM Gera datomic-deploy/, uma copia enxuta de datomic/ (sem os drivers de
REM Cassandra/DynamoDB, que nao sao usados pelo storage "dev") para caber
REM no limite de upload do "railway up". Rode depois de atualizar o Datomic.
setlocal enabledelayedexpansion
cd /d "%~dp0.."

if exist datomic-deploy rmdir /s /q datomic-deploy
mkdir datomic-deploy\lib

xcopy /e /i /y datomic\bin datomic-deploy\bin >nul
xcopy /e /i /y datomic\resources datomic-deploy\resources >nul
copy /y datomic\*.jar datomic-deploy\ >nul
copy /y datomic\entrypoint.sh datomic-deploy\ >nul
copy /y datomic\Dockerfile datomic-deploy\ >nul

for %%f in (datomic\lib\*.jar) do (
  set "fname=%%~nxf"
  echo !fname! | findstr /i /r "cassandra java-driver dynamodb" >nul
  if errorlevel 1 (
    copy /y "%%f" datomic-deploy\lib\ >nul
  )
)

echo datomic-deploy atualizado.
endlocal
