package runner

import (
	"fmt"
	"strings"

	"go.starlark.net/starlark"
)

// ValidationRequest represents a request to validate Starlark code
type ValidationRequest struct {
	Code string `json:"code"`
	Type string `json:"type"` // "automation" or "library"
}

// ValidationResult represents the result of code validation
type ValidationResult struct {
	Valid  bool     `json:"valid"`
	Errors []string `json:"errors,omitempty"`
}

// ValidateCode validates Starlark code without writing to disk
// For automations: checks syntax, config, and handler functions
// For libraries: checks syntax only
func ValidateCode(code string, fileType string) ValidationResult {
	// Check for empty code
	if strings.TrimSpace(code) == "" {
		return ValidationResult{
			Valid:  false,
			Errors: []string{"code cannot be empty"},
		}
	}

	// Default to automation validation if type is unknown
	if fileType != "library" {
		fileType = "automation"
	}

	// Execute the Starlark code to check for syntax errors
	thread := &starlark.Thread{Name: "validation"}
	globals, err := starlark.ExecFile(thread, "validation.star", []byte(code), nil)
	if err != nil {
		return ValidationResult{
			Valid:  false,
			Errors: []string{formatStarlarkError(err)},
		}
	}

	// For libraries, we only need syntax validation
	if fileType == "library" {
		return ValidationResult{Valid: true}
	}

	// For automations, perform additional validation
	return validateAutomation(globals)
}

// validateAutomation checks automation-specific requirements
func validateAutomation(globals starlark.StringDict) ValidationResult {
	var errors []string

	// Check for config variable
	configVal, ok := globals["config"]
	if !ok {
		errors = append(errors, "automation missing 'config' variable")
		return ValidationResult{Valid: false, Errors: errors}
	}

	// Validate config is a dict
	_, ok = configVal.(*starlark.Dict)
	if !ok {
		errors = append(errors, "config must be a dict")
		return ValidationResult{Valid: false, Errors: errors}
	}

	// Note: We could validate config structure here using extractConfig
	// but that would require exporting it. For now, we just check it's a dict.

	// Check for handler functions
	var hasOnMessage, hasOnSchedule bool

	if fn, ok := globals["on_message"]; ok {
		if _, isCallable := fn.(starlark.Callable); isCallable {
			hasOnMessage = true
		} else {
			errors = append(errors, "on_message must be a callable function")
		}
	}

	if fn, ok := globals["on_schedule"]; ok {
		if _, isCallable := fn.(starlark.Callable); isCallable {
			hasOnSchedule = true
		} else {
			errors = append(errors, "on_schedule must be a callable function")
		}
	}

	if !hasOnMessage && !hasOnSchedule {
		errors = append(errors, "automation must define on_message or on_schedule function")
	}

	if len(errors) > 0 {
		return ValidationResult{Valid: false, Errors: errors}
	}

	return ValidationResult{Valid: true}
}

// formatStarlarkError formats a Starlark error for display
func formatStarlarkError(err error) string {
	// Starlark errors include file:line:column information
	// We want to keep this but make it more readable
	errStr := err.Error()

	// Replace the temporary filename with something clearer
	errStr = strings.ReplaceAll(errStr, "validation.star:", "line ")

	return fmt.Sprintf("syntax error: %s", errStr)
}
