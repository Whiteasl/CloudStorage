#!/bin/sh

# 如果没有设置 JWT_SECRET ，就自动设置一个
if [ -z "$JWT_SECRET" ]; then
    export JWT_SECRET=$(openssl rand -base64 32)
    echo "[docker-entrypoint] 已自动生成 JWT_SECRET"
fi

exec java -jar app.jar
