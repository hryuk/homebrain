package watcher

import (
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"github.com/fsnotify/fsnotify"

	"github.com/homebrain/engine/internal/runner"
)

// Watcher watches for automation file changes
type Watcher struct {
	dir     string
	watcher *fsnotify.Watcher
	runner  *runner.Runner
}

// New creates a new file watcher
func New(dir string, runner *runner.Runner) (*Watcher, error) {
	fsWatcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, err
	}

	w := &Watcher{
		dir:     dir,
		watcher: fsWatcher,
		runner:  runner,
	}

	// Create directory if it doesn't exist
	if err := os.MkdirAll(dir, 0755); err != nil {
		return nil, err
	}

	// Watch main automations directory
	if err := fsWatcher.Add(dir); err != nil {
		fsWatcher.Close()
		return nil, err
	}

	// Watch lib subdirectory
	libDir := filepath.Join(dir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		return nil, err
	}
	if err := fsWatcher.Add(libDir); err != nil {
		slog.Error("Failed to watch lib directory", "error", err)
	}

	return w, nil
}

// Close stops the watcher
func (w *Watcher) Close() error {
	return w.watcher.Close()
}

// LoadAll loads all existing automations
func (w *Watcher) LoadAll() error {
	entries, err := os.ReadDir(w.dir)
	if err != nil {
		return err
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if !isStarlarkFile(entry.Name()) {
			continue
		}

		filePath := filepath.Join(w.dir, entry.Name())
		if err := w.runner.LoadAutomation(filePath); err != nil {
			slog.Error("Failed to load automation", "file", filePath, "error", err)
		}
	}

	return nil
}

// Watch starts watching for file changes
func (w *Watcher) Watch() {
	slog.Info("Starting file watcher", "dir", w.dir)

	for {
		select {
		case event, ok := <-w.watcher.Events:
			if !ok {
				return
			}

			if !isStarlarkFile(event.Name) {
				continue
			}

			slog.Debug("File event", "event", event.Op, "file", event.Name)

			switch {
			case event.Op&fsnotify.Create == fsnotify.Create:
				w.handleCreate(event.Name)
			case event.Op&fsnotify.Write == fsnotify.Write:
				w.handleWrite(event.Name)
			case event.Op&fsnotify.Remove == fsnotify.Remove:
				w.handleRemove(event.Name)
			case event.Op&fsnotify.Rename == fsnotify.Rename:
				w.handleRemove(event.Name)
			}

		case err, ok := <-w.watcher.Errors:
			if !ok {
				return
			}
			slog.Error("Watcher error", "error", err)
		}
	}
}

func (w *Watcher) handleCreate(filePath string) {
	// Check if it's a library file
	if isLibraryFile(filePath) {
		slog.Info("New library module detected", "file", filePath)
		w.reloadLibraries()
		return
	}
	
	slog.Info("New automation detected", "file", filePath)
	if err := w.runner.LoadAutomation(filePath); err != nil {
		slog.Error("Failed to load new automation", "file", filePath, "error", err)
	}
}

func (w *Watcher) handleWrite(filePath string) {
	// Check if it's a library file
	if isLibraryFile(filePath) {
		slog.Info("Library module updated", "file", filePath)
		w.reloadLibraries()
		return
	}
	
	slog.Info("Automation updated", "file", filePath)
	if err := w.runner.LoadAutomation(filePath); err != nil {
		slog.Error("Failed to reload automation", "file", filePath, "error", err)
	}
}

func (w *Watcher) handleRemove(filePath string) {
	// Check if it's a library file
	if isLibraryFile(filePath) {
		slog.Info("Library module removed", "file", filePath)
		w.reloadLibraries()
		return
	}
	
	slog.Info("Automation removed", "file", filePath)
	id := automationIDFromPath(filePath)
	w.runner.UnloadAutomation(id)
}

func isStarlarkFile(name string) bool {
	return strings.HasSuffix(name, ".star") || strings.HasSuffix(name, ".lib.star")
}

func isLibraryFile(filePath string) bool {
	return strings.Contains(filePath, "/lib/") && strings.HasSuffix(filePath, ".lib.star")
}

func automationIDFromPath(filePath string) string {
	base := filepath.Base(filePath)
	return strings.TrimSuffix(base, filepath.Ext(base))
}

func (w *Watcher) reloadLibraries() {
	slog.Info("Reloading all library modules")
	if err := w.runner.LoadLibraries(w.dir); err != nil {
		slog.Error("Failed to reload libraries", "error", err)
		return
	}
	
	// Reload all automations to pick up new library functions
	slog.Info("Reloading all automations to apply library changes")
	if err := w.LoadAll(); err != nil {
		slog.Error("Failed to reload automations after library change", "error", err)
	}
}
