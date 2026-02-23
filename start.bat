@echo off
title Compilador CompanyEconomy v2.0 - Java 17

echo ============================================
echo Compilador do Plugin CompanyEconomy
echo (Integracao com CoinCard)
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=

rem Procura JDK 17 em locais comuns
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\OpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\jdk17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo Instale o Java 17 JDK e tente novamente.
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

echo Limpando pasta out...
if exist out (
    rmdir /s /q out >nul 2>&1
)
mkdir out
mkdir out\com
mkdir out\com\foxsrv
mkdir out\com\foxsrv\companyeconomy

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

REM Verificar Spigot API
if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    echo.
    echo Certifique-se de que o arquivo está na pasta raiz.
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado
)

REM Verificar Gson (necessario para JSON)
if not exist libs\gson-2.10.1.jar (
    echo [AVISO] gson-2.10.1.jar nao encontrado em libs\
    echo Usando Gson embutido no Spigot...
    echo.
) else (
    echo [OK] Gson encontrado em libs\
)

REM Verificar CoinCard API
if not exist CoinCard.jar (
    echo [AVISO] CoinCard.jar nao encontrado na pasta raiz!
    echo O plugin CompanyEconomy requer o CoinCard como dependencia.
    echo.
    echo Certifique-se de que o CoinCard.jar esta na pasta plugins do servidor.
    echo Continuando compilacao mesmo assim...
    echo.
    set COINCARD_PATH=
) else (
    echo [OK] CoinCard API encontrado
    set COINCARD_PATH=CoinCard.jar
)

echo.
echo ============================================
echo Compilando CompanyEconomy...
echo ============================================
echo.

REM Montar classpath
set CLASSPATH="spigot-api-1.20.1-R0.1-SNAPSHOT.jar"
if exist libs\gson-2.10.1.jar (
    set CLASSPATH=%CLASSPATH%;libs\gson-2.10.1.jar
)
if defined COINCARD_PATH (
    set CLASSPATH=%CLASSPATH%;CoinCard.jar
)

REM Compilar com as dependências necessárias
%JAVAC% --release 17 -d out ^
-classpath %CLASSPATH% ^
src/com/foxsrv/companyeconomy/CompanyEconomy.java

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    echo.
    echo Verifique os erros acima e corrija o codigo.
    pause
    exit /b 1
)

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

REM Copiar plugin.yml
if exist resources\plugin.yml (
    copy resources\plugin.yml out\ >nul
    echo [OK] plugin.yml copiado
) else (
    echo [AVISO] plugin.yml nao encontrado em resources\
    echo Criando plugin.yml padrao...
    
    (
        echo name: CompanyEconomy
        echo version: 2.0
        echo main: com.foxsrv.companyeconomy.CompanyEconomy
        echo api-version: 1.20
        echo author: FoxOficial2
        echo description: Company management plugin with CoinCard integration
        echo depend: [CoinCard]
        echo.
        echo commands:
        echo   company:
        echo     description: Main company command
        echo     aliases: [comp, companies]
        echo     usage: /company [hire^|fire^|leave^|deposit^|withdraw^|reload^|info]
        echo.
        echo permissions:
        echo   company.*:
        echo     description: All CompanyEconomy permissions
        echo     default: op
        echo     children:
        echo       company.reload: true
        echo       company.admin: true
        echo   company.reload:
        echo     description: Reload the plugin configuration
        echo     default: op
        echo   company.admin:
        echo     description: Admin permissions
        echo     default: op
    ) > out\plugin.yml
    echo [OK] plugin.yml criado automaticamente
)

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out

REM Criar JAR com todos os recursos
%JAR% cf CompanyEconomy.jar com plugin.yml

cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\CompanyEconomy.jar
echo.
dir out\CompanyEconomy.jar
echo.
echo ============================================
echo IMPORTANTE - REQUISITOS PARA EXECUCAO:
echo ============================================
echo.
echo 1 - O plugin CoinCard DEVE estar instalado no servidor
echo 2 - Adicione no plugin.xml do CoinCard:
echo     ^<permission^>
echo         ^<name^>coincard.api^</name^>
echo     ^</permission^>
echo 3 - Certifique-se de que ambos os plugins estao na pasta plugins/
echo.
echo ============================================
echo Para instalar:
echo 1 - Copie out\CompanyEconomy.jar para a pasta plugins do servidor
echo 2 - Copie CoinCard.jar para a pasta plugins do servidor
echo 3 - Reinicie o servidor ou use /reload confirm
echo ============================================
echo.

pause