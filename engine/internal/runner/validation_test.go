package runner

import (
	"strings"
	"testing"
)

func TestValidateCode_ValidAutomation(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.log("Received message")

config = {
    "name": "Test Automation",
    "description": "A test automation",
    "subscribe": ["test/topic"],
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if !result.Valid {
		t.Errorf("Expected valid code, got errors: %v", result.Errors)
	}
}

func TestValidateCode_ValidAutomationWithSchedule(t *testing.T) {
	code := `
def on_schedule(ctx):
    ctx.log("Scheduled task")

config = {
    "name": "Scheduled Automation",
    "description": "Runs on schedule",
    "schedule": "* * * * *",
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if !result.Valid {
		t.Errorf("Expected valid code, got errors: %v", result.Errors)
	}
}

func TestValidateCode_ValidAutomationWithBothHandlers(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.log("Message received")

def on_schedule(ctx):
    ctx.log("Scheduled task")

config = {
    "name": "Dual Handler Automation",
    "description": "Has both handlers",
    "subscribe": ["test/topic"],
    "schedule": "* * * * *",
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if !result.Valid {
		t.Errorf("Expected valid code, got errors: %v", result.Errors)
	}
}

func TestValidateCode_SyntaxError(t *testing.T) {
	code := `
def on_message(topic, payload, ctx)  # Missing colon
    ctx.log("test")

config = {"name": "Test"}
`
	result := ValidateCode(code, "automation")

	if result.Valid {
		t.Error("Expected invalid code due to syntax error")
	}

	if len(result.Errors) == 0 {
		t.Error("Expected at least one error message")
	}

	// Should mention syntax error
	hasParseError := false
	for _, err := range result.Errors {
		if strings.Contains(strings.ToLower(err), "syntax") || strings.Contains(err, "got newline") {
			hasParseError = true
			break
		}
	}
	if !hasParseError {
		t.Errorf("Expected syntax error message, got: %v", result.Errors)
	}
}

func TestValidateCode_MissingConfig(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.log("test")
`
	result := ValidateCode(code, "automation")

	if result.Valid {
		t.Error("Expected invalid code due to missing config")
	}

	// Should mention missing config
	hasConfigError := false
	for _, err := range result.Errors {
		if strings.Contains(strings.ToLower(err), "config") {
			hasConfigError = true
			break
		}
	}
	if !hasConfigError {
		t.Errorf("Expected error about missing config, got: %v", result.Errors)
	}
}

func TestValidateCode_ConfigNotDict(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.log("test")

config = "not a dict"
`
	result := ValidateCode(code, "automation")

	if result.Valid {
		t.Error("Expected invalid code due to config not being a dict")
	}

	// Should mention config must be a dict
	hasConfigError := false
	for _, err := range result.Errors {
		if strings.Contains(strings.ToLower(err), "config") && strings.Contains(strings.ToLower(err), "dict") {
			hasConfigError = true
			break
		}
	}
	if !hasConfigError {
		t.Errorf("Expected error about config being a dict, got: %v", result.Errors)
	}
}

func TestValidateCode_MissingHandler(t *testing.T) {
	code := `
config = {
    "name": "No Handler Automation",
    "description": "Missing handler",
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if result.Valid {
		t.Error("Expected invalid code due to missing handler")
	}

	// Should mention missing handler
	hasHandlerError := false
	for _, err := range result.Errors {
		if strings.Contains(strings.ToLower(err), "on_message") || strings.Contains(strings.ToLower(err), "on_schedule") {
			hasHandlerError = true
			break
		}
	}
	if !hasHandlerError {
		t.Errorf("Expected error about missing handler, got: %v", result.Errors)
	}
}

func TestValidateCode_HandlerNotCallable(t *testing.T) {
	code := `
on_message = "not a function"

config = {
    "name": "Bad Handler",
    "description": "Handler not callable",
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if result.Valid {
		t.Error("Expected invalid code due to non-callable handler")
	}
}

func TestValidateCode_ValidLibrary(t *testing.T) {
	code := `
"""A test library module."""

def helper_function(ctx, value):
    """Helper function that does something useful."""
    return value * 2

def another_function(ctx):
    """Another helper function."""
    ctx.log("Hello from library")
`
	result := ValidateCode(code, "library")

	if !result.Valid {
		t.Errorf("Expected valid library code, got errors: %v", result.Errors)
	}
}

func TestValidateCode_LibrarySyntaxError(t *testing.T) {
	code := `
def broken_function(ctx)  # Missing colon
    return ctx
`
	result := ValidateCode(code, "library")

	if result.Valid {
		t.Error("Expected invalid library due to syntax error")
	}

	if len(result.Errors) == 0 {
		t.Error("Expected at least one error message")
	}
}

func TestValidateCode_LibraryDoesNotRequireConfig(t *testing.T) {
	code := `
def utility_function(x):
    return x + 1
`
	result := ValidateCode(code, "library")

	if !result.Valid {
		t.Errorf("Library should not require config, got errors: %v", result.Errors)
	}
}

func TestValidateCode_LibraryDoesNotRequireHandlers(t *testing.T) {
	code := `
# Simple library with no on_message or on_schedule
def process_data(data):
    return data.upper()
`
	result := ValidateCode(code, "library")

	if !result.Valid {
		t.Errorf("Library should not require handlers, got errors: %v", result.Errors)
	}
}

func TestValidateCode_EmptyCode(t *testing.T) {
	result := ValidateCode("", "automation")

	if result.Valid {
		t.Error("Expected invalid for empty code")
	}
}

func TestValidateCode_WhitespaceOnlyCode(t *testing.T) {
	result := ValidateCode("   \n\t\n   ", "automation")

	if result.Valid {
		t.Error("Expected invalid for whitespace-only code")
	}
}

func TestValidateCode_UnknownType(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    pass

config = {"name": "Test", "enabled": True}
`
	// Unknown type should default to automation validation
	result := ValidateCode(code, "unknown")

	if !result.Valid {
		t.Errorf("Unknown type should default to automation validation, got errors: %v", result.Errors)
	}
}

func TestValidateCode_UndefinedVariable(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.log(undefined_variable)

config = {
    "name": "Test",
    "enabled": True,
}
`
	// Note: Starlark doesn't catch undefined variables at parse time,
	// only at runtime. So this should still be "valid" from syntax perspective.
	result := ValidateCode(code, "automation")

	// This depends on whether we want to do runtime validation too
	// For now, we only do static/syntax validation
	if !result.Valid {
		t.Logf("Note: Undefined variable error caught: %v", result.Errors)
	}
}

func TestValidateCode_GlobalStateWritesValid(t *testing.T) {
	code := `
def on_message(topic, payload, ctx):
    ctx.set_global("presence.room", True)

config = {
    "name": "Global State Automation",
    "description": "Uses global state",
    "subscribe": ["test/topic"],
    "global_state_writes": ["presence.*"],
    "enabled": True,
}
`
	result := ValidateCode(code, "automation")

	if !result.Valid {
		t.Errorf("Expected valid code with global_state_writes, got errors: %v", result.Errors)
	}
}
