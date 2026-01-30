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

	// Load library modules
	if err := automationRunner.LoadLibraries("/app/automations"); err != nil {
		slog.Error("Failed to load library modules", "error", err)
	} else {
		slog.Info("Library modules loaded successfully")
	}

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
	go startAPI(automationRunner, mqttClient, stateStore)

	// Wait for shutdown signal
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)
	<-sigChan

	slog.Info("Shutting down Homebrain Automation Engine")
}

func startAPI(r *runner.Runner, mqttClient *mqtt.Client, stateStore *state.Store) {
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

	// List library modules
	mux.HandleFunc("GET /library", func(w http.ResponseWriter, req *http.Request) {
		libManager := r.GetLibraryManager()
		modules := libManager.GetAllModules()
		
		// Convert to a simplified response format
		type LibraryModuleResponse struct {
			Name        string   `json:"name"`
			Description string   `json:"description"`
			Functions   []string `json:"functions"`
		}
		
		response := make([]LibraryModuleResponse, 0, len(modules))
		for _, module := range modules {
			funcNames := make([]string, 0, len(module.Functions))
			for name := range module.Functions {
				funcNames = append(funcNames, name)
			}
			response = append(response, LibraryModuleResponse{
				Name:        module.Name,
				Description: module.Description,
				Functions:   funcNames,
			})
		}
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(response)
	})

	// Get library module source code
	mux.HandleFunc("GET /library/{name}", func(w http.ResponseWriter, req *http.Request) {
		name := req.PathValue("name")
		libManager := r.GetLibraryManager()
		module, ok := libManager.GetModule(name)
		if !ok {
			http.Error(w, "Module not found", http.StatusNotFound)
			return
		}
		
		// Read the source file
		content, err := os.ReadFile(module.FilePath)
		if err != nil {
			http.Error(w, "Failed to read module source", http.StatusInternalServerError)
			return
		}
		
		w.Header().Set("Content-Type", "text/plain")
		w.Write(content)
	})

	// Get global state
	mux.HandleFunc("GET /global-state", func(w http.ResponseWriter, req *http.Request) {
		globalState, err := stateStore.GetAllGlobalState()
		if err != nil {
			http.Error(w, "Failed to get global state", http.StatusInternalServerError)
			return
		}
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(globalState)
	})

	// Get global state schema (which automations write which keys)
	mux.HandleFunc("GET /global-state-schema", func(w http.ResponseWriter, req *http.Request) {
		automations := r.ListAutomations()
		
		// Build schema: key patterns -> automation IDs
		schema := make(map[string][]string)
		for _, automation := range automations {
			for _, pattern := range automation.Config.GlobalStateWrites {
				schema[pattern] = append(schema[pattern], automation.ID)
			}
		}
		
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(schema)
	})

	slog.Info("Starting Engine API", "port", 9000)
	if err := http.ListenAndServe(":9000", mux); err != nil {
		slog.Error("API server failed", "error", err)
	}
}
