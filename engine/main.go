package main

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/homebrain/engine/internal/mqtt"
	"github.com/homebrain/engine/internal/runner"
	"github.com/homebrain/engine/internal/state"
	"github.com/homebrain/engine/internal/watcher"
)

func main() {
	// Setup logging
	logLevel := os.Getenv("LOG_LEVEL")
	var level slog.Level
	switch logLevel {
	case "debug":
		level = slog.LevelDebug
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}
	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: level}))
	slog.SetDefault(logger)

	slog.Info("Starting Homebrain Automation Engine")

	// Initialize state store
	stateStore, err := state.New("/app/state/homebrain.db")
	if err != nil {
		slog.Error("Failed to initialize state store", "error", err)
		os.Exit(1)
	}
	defer stateStore.Close()

	// Initialize MQTT client
	broker := os.Getenv("MQTT_BROKER")
	if broker == "" {
		slog.Error("MQTT_BROKER environment variable is required")
		os.Exit(1)
	}

	mqttClient, err := mqtt.New(mqtt.Config{
		Broker:   broker,
		Username: os.Getenv("MQTT_USERNAME"),
		Password: os.Getenv("MQTT_PASSWORD"),
		ClientID: "homebrain-engine",
	})
	if err != nil {
		slog.Error("Failed to connect to MQTT broker", "error", err)
		os.Exit(1)
	}
	defer mqttClient.Disconnect()

	slog.Info("Connected to MQTT broker", "broker", broker)

	// Initialize automation runner
	automationRunner := runner.New(mqttClient, stateStore)

	// Initialize file watcher
	fileWatcher, err := watcher.New("/app/automations", automationRunner)
	if err != nil {
		slog.Error("Failed to initialize file watcher", "error", err)
		os.Exit(1)
	}
	defer fileWatcher.Close()

	// Load existing automations
	if err := fileWatcher.LoadAll(); err != nil {
		slog.Error("Failed to load automations", "error", err)
	}

	// Start file watcher
	go fileWatcher.Watch()

	// Start HTTP API for agent communication
	go startAPI(automationRunner, mqttClient)

	// Wait for shutdown signal
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan

	slog.Info("Shutting down Homebrain Automation Engine")
}

func startAPI(r *runner.Runner, mqttClient *mqtt.Client) {
	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, req *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("ok"))
	})

	// List automations
	mux.HandleFunc("GET /automations", func(w http.ResponseWriter, req *http.Request) {
		automations := r.ListAutomations()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(automations)
	})

	// Get discovered topics
	mux.HandleFunc("GET /topics", func(w http.ResponseWriter, req *http.Request) {
		topics := mqttClient.GetDiscoveredTopics()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(topics)
	})

	// Get logs (recent log entries)
	mux.HandleFunc("GET /logs", func(w http.ResponseWriter, req *http.Request) {
		logs := r.GetLogs()
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(logs)
	})

	slog.Info("Starting Engine API", "port", 9000)
	if err := http.ListenAndServe(":9000", mux); err != nil {
		slog.Error("API server failed", "error", err)
	}
}
