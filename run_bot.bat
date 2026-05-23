@echo off
title MyJavaBot - Auto Restart

:loop
echo [%date% %time%] Starting bot...
java -Dfile.encoding=UTF-8 -jar C:\Code\TelegramBot\target\TelegramBot-1.0.jar
echo [%date% %time%] Bot stopped! Restarting in 5 seconds...
timeout /t 5 /nobreak
goto loop