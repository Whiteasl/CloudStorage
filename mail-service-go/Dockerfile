FROM golang:1.26-alpine AS build
WORKDIR /src
ENV GOPROXY=https://goproxy.cn,direct 
ENV GOSUMDB=off
ENV GOTOOLCHAIN=local
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/mail-service-go .

FROM alpine:3.21
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
COPY --from=build /out/mail-service-go /app/mail-service-go
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s CMD wget
ENTRYPOINT [ "/app/mail-service-go" ]

