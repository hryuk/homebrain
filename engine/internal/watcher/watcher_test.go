package watcher

import (
	"testing"
)

func TestIsStarlarkFile(t *testing.T) {
	tests := []struct {
		name     string
		filename string
		expected bool
	}{
		{"Regular automation file", "motion_light.star", true},
		{"Library file", "timers.lib.star", true},
		{"Python file", "script.py", false},
		{"Text file", "readme.txt", false},
		{"JSON file", "config.json", false},
		{"No extension", "makefile", false},
		{"Star in name but not extension", "starwars.txt", false},
		{"Full path automation", "/app/automations/test.star", true},
		{"Full path library", "/app/automations/lib/utils.lib.star", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := isStarlarkFile(tt.filename)
			if result != tt.expected {
				t.Errorf("isStarlarkFile(%q) = %v, want %v", tt.filename, result, tt.expected)
			}
		})
	}
}

func TestIsLibraryFile(t *testing.T) {
	tests := []struct {
		name     string
		filepath string
		expected bool
	}{
		{"Library in lib directory", "/app/automations/lib/timers.lib.star", true},
		{"Library with relative path", "automations/lib/utils.lib.star", true},
		{"Regular automation", "/app/automations/motion_light.star", false},
		{"Star file in lib (not .lib.star)", "/app/automations/lib/test.star", false},
		{"Library file outside lib dir", "/app/automations/timers.lib.star", false},
		{"Nested lib directory", "/app/automations/lib/nested/file.lib.star", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := isLibraryFile(tt.filepath)
			if result != tt.expected {
				t.Errorf("isLibraryFile(%q) = %v, want %v", tt.filepath, result, tt.expected)
			}
		})
	}
}

func TestAutomationIDFromPath(t *testing.T) {
	tests := []struct {
		name     string
		filepath string
		expected string
	}{
		{"Simple file", "motion_light.star", "motion_light"},
		{"Full path", "/app/automations/temperature_sensor.star", "temperature_sensor"},
		{"With underscores", "/path/to/my_cool_automation.star", "my_cool_automation"},
		{"Library file", "/app/automations/lib/utils.lib.star", "utils.lib"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := automationIDFromPath(tt.filepath)
			if result != tt.expected {
				t.Errorf("automationIDFromPath(%q) = %q, want %q", tt.filepath, result, tt.expected)
			}
		})
	}
}
