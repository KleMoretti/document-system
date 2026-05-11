package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"time"

	_ "github.com/go-sql-driver/mysql"

	"documentation-collab/backend-go/internal"
)

func main() {
	cfg := internal.LoadConfig()
	if cfg.JWTSecret == "" || cfg.JWTSecret == "change-this-development-secret" {
		log.Fatal("JWT_SECRET must be set to a non-default value")
	}
	db, err := sql.Open("mysql", cfg.MySQLDSN())
	if err != nil {
		log.Fatalf("open mysql: %v", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		log.Fatalf("ping mysql: %v", err)
	}

	store := internal.NewStore(db)
	auth := internal.NewJWTManager(cfg.JWTSecret, 24*time.Hour)
	hub := internal.NewHub(cfg.InstanceID)
	redisBus := internal.NewRedisBus(cfg, hub)
	go redisBus.Run(context.Background())

	server := internal.NewServer(cfg, store, auth, hub, redisBus)
	log.Printf("go backend listening on %s", cfg.HTTPAddr)
	if err := http.ListenAndServe(cfg.HTTPAddr, server.Routes()); err != nil {
		log.Fatal(err)
	}
}
