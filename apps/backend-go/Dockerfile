FROM golang:1.24-alpine AS build

WORKDIR /src
COPY apps/backend-go/go.mod apps/backend-go/go.sum ./
RUN go mod download

COPY apps/backend-go ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/backend-go .

FROM alpine:3.21

WORKDIR /app
RUN adduser -D -H appuser
COPY --from=build /out/backend-go /app/backend-go

USER appuser
EXPOSE 8081
ENTRYPOINT ["/app/backend-go"]
