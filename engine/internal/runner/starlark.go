package runner

import (
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/robfig/cron/v3"
	"go.starlark.net/starlark"

	"github.com/homebrain/engine/internal/mqtt"
	"github.com/homebrain/engine/internal/state"
)

// AutomationConfig represents the config dict from a Starlark automation
type AutomationConfig struct {
	Name              string   `json:"name"`
	Description       string   `json:"description"`
	Subscribe         []string `json:"subscribe"`
	Schedule          string   `json:"schedule,omitempty"`
	Enabled           bool     `json:"enabled"`
	GlobalStateWrites []string `json:"global_state_writes,omitempty"`
}

// Automation represents a loaded automation
type Automation struct {
	ID           string           `json:"id"`
	FilePath     string           `json:"file_path"`
	Config       AutomationConfig `json:"config"`
	globals      starlark.StringDict
	onMessage    starlark.Callable
	onSchedule   starlark.Callable
	cronEntryID  cron.EntryID
	context      *Context
}

// LogEntry represents a log message from an automation
type LogEntry struct {
	Timestamp    time.Time `json:"timestamp"`
	AutomationID string    `json:"automation_id"`
	Message      string    `json:"message"`
}

// Runner manages and executes Starlark automations
type Runner struct {
	mqttClient     *mqtt.Client
	stateStore     *state.Store
	automations    map[string]*Automation
	libraryManager *LibraryManager
	mu             sync.RWMutex
	cron           *cron.Cron
	logs           []LogEntry
	logsMu         sync.RWMutex
	maxLogs        int
}

// New creates a new automation runner
func New(mqttClient *mqtt.Client, stateStore *state.Store) *Runner {
	r := &Runner{
		mqttClient:     mqttClient,
		stateStore:     stateStore,
		automations:    make(map[string]*Automation),
		libraryManager: NewLibraryManager(),
		cron:           cron.New(),
		logs:           make([]LogEntry, 0, 1000),
		maxLogs:        1000,
	}
	r.cron.Start()
	return r
}

// LoadLibraries loads all library modules from the automations/lib directory
func (r *Runner) LoadLibraries(automationsPath string) error {
	return r.libraryManager.LoadLibraries(automationsPath)
}

// GetLibraryManager returns the library manager
func (r *Runner) GetLibraryManager() *LibraryManager {
	return r.libraryManager
}

// LoadAutomation loads a Starlark automation from a file
func (r *Runner) LoadAutomation(filePath string) error {
	id := automationIDFromPath(filePath)

	// Unload existing automation if present
	r.UnloadAutomation(id)

	slog.Info("Loading automation", "id", id, "path", filePath)

	// Read file
	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("failed to read automation file: %w", err)
	}

	// Parse and execute Starlark
	thread := &starlark.Thread{Name: id}
	globals, err := starlark.ExecFile(thread, filePath, data, nil)
	if err != nil {
		return fmt.Errorf("failed to execute automation: %w", err)
	}

	// Extract config
	configVal, ok := globals["config"]
	if !ok {
		return fmt.Errorf("automation missing 'config' variable")
	}

	config, err := extractConfig(configVal)
	if err != nil {
		return fmt.Errorf("invalid config: %w", err)
	}

	if !config.Enabled {
		slog.Info("Automation disabled, skipping", "id", id)
		return nil
	}

	// Extract handlers
	var onMessage, onSchedule starlark.Callable
	if fn, ok := globals["on_message"]; ok {
		if callable, ok := fn.(starlark.Callable); ok {
			onMessage = callable
		}
	}
	if fn, ok := globals["on_schedule"]; ok {
		if callable, ok := fn.(starlark.Callable); ok {
			onSchedule = callable
		}
	}

	if onMessage == nil && onSchedule == nil {
		return fmt.Errorf("automation must define on_message or on_schedule function")
	}

	// Create automation context
	ctx := NewContext(id, r.mqttClient, r.stateStore, r.addLog, config.GlobalStateWrites, r.libraryManager)

	automation := &Automation{
		ID:         id,
		FilePath:   filePath,
		Config:     config,
		globals:    globals,
		onMessage:  onMessage,
		onSchedule: onSchedule,
		context:    ctx,
	}

	// Subscribe to MQTT topics
	if onMessage != nil && len(config.Subscribe) > 0 {
		for _, topic := range config.Subscribe {
			topicCopy := topic
			err := r.mqttClient.Subscribe(topic, func(t string, payload []byte) {
				r.handleMessage(automation, t, payload)
			})
			if err != nil {
				slog.Error("Failed to subscribe to topic", "topic", topicCopy, "error", err)
			}
		}
	}

	// Setup cron schedule
	if onSchedule != nil && config.Schedule != "" {
		entryID, err := r.cron.AddFunc(config.Schedule, func() {
			r.handleSchedule(automation)
		})
		if err != nil {
			slog.Error("Failed to add cron schedule", "schedule", config.Schedule, "error", err)
		} else {
			automation.cronEntryID = entryID
		}
	}

	r.mu.Lock()
	r.automations[id] = automation
	r.mu.Unlock()

	slog.Info("Automation loaded", "id", id, "name", config.Name, "topics", config.Subscribe)
	return nil
}

// UnloadAutomation unloads an automation
func (r *Runner) UnloadAutomation(id string) {
	r.mu.Lock()
	automation, exists := r.automations[id]
	if exists {
		// Unsubscribe from topics
		for _, topic := range automation.Config.Subscribe {
			r.mqttClient.Unsubscribe(topic)
		}
		// Remove cron job
		if automation.cronEntryID != 0 {
			r.cron.Remove(automation.cronEntryID)
		}
		delete(r.automations, id)
		slog.Info("Automation unloaded", "id", id)
	}
	r.mu.Unlock()
}

// ListAutomations returns all loaded automations
func (r *Runner) ListAutomations() []Automation {
	r.mu.RLock()
	defer r.mu.RUnlock()

	result := make([]Automation, 0, len(r.automations))
	for _, a := range r.automations {
		result = append(result, Automation{
			ID:       a.ID,
			FilePath: a.FilePath,
			Config:   a.Config,
		})
	}
	return result
}

// GetLogs returns recent log entries
func (r *Runner) GetLogs() []LogEntry {
	r.logsMu.RLock()
	defer r.logsMu.RUnlock()

	result := make([]LogEntry, len(r.logs))
	copy(result, r.logs)
	return result
}

func (r *Runner) addLog(automationID, message string) {
	r.logsMu.Lock()
	defer r.logsMu.Unlock()

	entry := LogEntry{
		Timestamp:    time.Now(),
		AutomationID: automationID,
		Message:      message,
	}

	r.logs = append(r.logs, entry)
	if len(r.logs) > r.maxLogs {
		r.logs = r.logs[len(r.logs)-r.maxLogs:]
	}

	slog.Info("Automation log", "automation", automationID, "message", message)
}

func (r *Runner) handleMessage(automation *Automation, topic string, payload []byte) {
	if automation.onMessage == nil {
		return
	}

	thread := &starlark.Thread{Name: automation.ID}
	ctx := automation.context.ToStarlark()

	_, err := starlark.Call(thread, automation.onMessage, starlark.Tuple{
		starlark.String(topic),
		starlark.String(payload),
		ctx,
	}, nil)

	if err != nil {
		slog.Error("Automation on_message error", "automation", automation.ID, "error", err)
		r.addLog(automation.ID, fmt.Sprintf("ERROR: %s", err))
	}
}

func (r *Runner) handleSchedule(automation *Automation) {
	if automation.onSchedule == nil {
		return
	}

	thread := &starlark.Thread{Name: automation.ID}
	ctx := automation.context.ToStarlark()

	_, err := starlark.Call(thread, automation.onSchedule, starlark.Tuple{ctx}, nil)

	if err != nil {
		slog.Error("Automation on_schedule error", "automation", automation.ID, "error", err)
		r.addLog(automation.ID, fmt.Sprintf("ERROR: %s", err))
	}
}

func automationIDFromPath(filePath string) string {
	base := filepath.Base(filePath)
	return strings.TrimSuffix(base, filepath.Ext(base))
}

func extractConfig(val starlark.Value) (AutomationConfig, error) {
	dict, ok := val.(*starlark.Dict)
	if !ok {
		return AutomationConfig{}, fmt.Errorf("config must be a dict")
	}

	config := AutomationConfig{Enabled: true}

	if v, found, _ := dict.Get(starlark.String("name")); found {
		if s, ok := v.(starlark.String); ok {
			config.Name = string(s)
		}
	}

	if v, found, _ := dict.Get(starlark.String("description")); found {
		if s, ok := v.(starlark.String); ok {
			config.Description = string(s)
		}
	}

	if v, found, _ := dict.Get(starlark.String("subscribe")); found {
		if list, ok := v.(*starlark.List); ok {
			for i := 0; i < list.Len(); i++ {
				if s, ok := list.Index(i).(starlark.String); ok {
					config.Subscribe = append(config.Subscribe, string(s))
				}
			}
		}
	}

	if v, found, _ := dict.Get(starlark.String("schedule")); found {
		if s, ok := v.(starlark.String); ok {
			config.Schedule = string(s)
		}
	}

	if v, found, _ := dict.Get(starlark.String("enabled")); found {
		if b, ok := v.(starlark.Bool); ok {
			config.Enabled = bool(b)
		}
	}

	if v, found, _ := dict.Get(starlark.String("global_state_writes")); found {
		if list, ok := v.(*starlark.List); ok {
			for i := 0; i < list.Len(); i++ {
				if s, ok := list.Index(i).(starlark.String); ok {
					config.GlobalStateWrites = append(config.GlobalStateWrites, string(s))
				}
			}
		}
	}

	return config, nil
}
