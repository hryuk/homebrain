package runner

import (
	"testing"

	"go.starlark.net/starlark"
)

func TestMatchPattern(t *testing.T) {
	tests := []struct {
		name     string
		pattern  string
		key      string
		expected bool
	}{
		// Exact matches
		{"Exact match", "presence.room", "presence.room", true},
		{"Exact match with dots", "devices.living_room.state", "devices.living_room.state", true},
		{"No match - different key", "presence.room", "presence.other", false},
		{"No match - longer key", "presence", "presence.room", false},
		{"No match - shorter key", "presence.room.state", "presence.room", false},

		// Wildcard matches
		{"Wildcard match - prefix", "presence.*", "presence.room", true},
		{"Wildcard match - deeper path", "devices.*", "devices.living_room.state", true},
		{"Wildcard match - exact prefix", "presence.*", "presence.", true},
		{"Wildcard no match - different prefix", "presence.*", "devices.room", false},
		{"Wildcard match - single char after", "test.*", "test.x", true},

		// Edge cases
		{"Empty pattern", "", "", true},
		{"Empty pattern with key", "", "something", false},
		{"Pattern with empty key", "test", "", false},
		{"Just wildcard", "*", "anything", true},
		{"Just wildcard with empty", "*", "", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := matchPattern(tt.pattern, tt.key)
			if result != tt.expected {
				t.Errorf("matchPattern(%q, %q) = %v, want %v", tt.pattern, tt.key, result, tt.expected)
			}
		})
	}
}

func TestStarlarkToGo(t *testing.T) {
	tests := []struct {
		name     string
		input    starlark.Value
		expected any
	}{
		{"None", starlark.None, nil},
		{"True", starlark.True, true},
		{"False", starlark.False, false},
		{"Integer", starlark.MakeInt(42), int64(42)},
		{"Float", starlark.Float(3.14), 3.14},
		{"String", starlark.String("hello"), "hello"},
		{"Empty string", starlark.String(""), ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := starlarkToGo(tt.input)
			if result != tt.expected {
				t.Errorf("starlarkToGo(%v) = %v (%T), want %v (%T)", tt.input, result, result, tt.expected, tt.expected)
			}
		})
	}
}

func TestStarlarkToGo_List(t *testing.T) {
	list := starlark.NewList([]starlark.Value{
		starlark.MakeInt(1),
		starlark.String("two"),
		starlark.True,
	})

	result := starlarkToGo(list)
	arr, ok := result.([]any)
	if !ok {
		t.Fatalf("Expected []any, got %T", result)
	}

	if len(arr) != 3 {
		t.Errorf("Expected 3 elements, got %d", len(arr))
	}

	if arr[0] != int64(1) {
		t.Errorf("Expected arr[0] = 1, got %v", arr[0])
	}
	if arr[1] != "two" {
		t.Errorf("Expected arr[1] = 'two', got %v", arr[1])
	}
	if arr[2] != true {
		t.Errorf("Expected arr[2] = true, got %v", arr[2])
	}
}

func TestStarlarkToGo_Dict(t *testing.T) {
	dict := starlark.NewDict(2)
	dict.SetKey(starlark.String("name"), starlark.String("test"))
	dict.SetKey(starlark.String("value"), starlark.MakeInt(42))

	result := starlarkToGo(dict)
	m, ok := result.(map[string]any)
	if !ok {
		t.Fatalf("Expected map[string]any, got %T", result)
	}

	if len(m) != 2 {
		t.Errorf("Expected 2 keys, got %d", len(m))
	}

	if m["name"] != "test" {
		t.Errorf("Expected m['name'] = 'test', got %v", m["name"])
	}
	if m["value"] != int64(42) {
		t.Errorf("Expected m['value'] = 42, got %v", m["value"])
	}
}

func TestStarlarkToGo_NestedStructure(t *testing.T) {
	innerDict := starlark.NewDict(1)
	innerDict.SetKey(starlark.String("nested"), starlark.String("value"))

	outerDict := starlark.NewDict(1)
	outerDict.SetKey(starlark.String("outer"), innerDict)

	result := starlarkToGo(outerDict)
	m, ok := result.(map[string]any)
	if !ok {
		t.Fatalf("Expected map[string]any, got %T", result)
	}

	inner, ok := m["outer"].(map[string]any)
	if !ok {
		t.Fatalf("Expected nested map[string]any, got %T", m["outer"])
	}

	if inner["nested"] != "value" {
		t.Errorf("Expected nested value, got %v", inner["nested"])
	}
}

func TestGoToStarlark(t *testing.T) {
	tests := []struct {
		name     string
		input    any
		checkFn  func(starlark.Value) bool
	}{
		{
			"nil to None",
			nil,
			func(v starlark.Value) bool { return v == starlark.None },
		},
		{
			"bool true",
			true,
			func(v starlark.Value) bool { return v == starlark.True },
		},
		{
			"bool false",
			false,
			func(v starlark.Value) bool { return v == starlark.False },
		},
		{
			"int",
			42,
			func(v starlark.Value) bool {
				i, ok := v.(starlark.Int)
				if !ok {
					return false
				}
				val, _ := i.Int64()
				return val == 42
			},
		},
		{
			"int64",
			int64(100),
			func(v starlark.Value) bool {
				i, ok := v.(starlark.Int)
				if !ok {
					return false
				}
				val, _ := i.Int64()
				return val == 100
			},
		},
		{
			"float64",
			3.14,
			func(v starlark.Value) bool {
				f, ok := v.(starlark.Float)
				return ok && float64(f) == 3.14
			},
		},
		{
			"string",
			"hello",
			func(v starlark.Value) bool {
				s, ok := v.(starlark.String)
				return ok && string(s) == "hello"
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := goToStarlark(tt.input)
			if !tt.checkFn(result) {
				t.Errorf("goToStarlark(%v) = %v, check failed", tt.input, result)
			}
		})
	}
}

func TestGoToStarlark_Slice(t *testing.T) {
	input := []any{1, "two", true}
	result := goToStarlark(input)

	list, ok := result.(*starlark.List)
	if !ok {
		t.Fatalf("Expected *starlark.List, got %T", result)
	}

	if list.Len() != 3 {
		t.Errorf("Expected 3 elements, got %d", list.Len())
	}
}

func TestGoToStarlark_Map(t *testing.T) {
	input := map[string]any{
		"name":  "test",
		"value": 42,
	}
	result := goToStarlark(input)

	dict, ok := result.(*starlark.Dict)
	if !ok {
		t.Fatalf("Expected *starlark.Dict, got %T", result)
	}

	if dict.Len() != 2 {
		t.Errorf("Expected 2 keys, got %d", dict.Len())
	}

	val, found, _ := dict.Get(starlark.String("name"))
	if !found {
		t.Error("Expected to find 'name' key")
	}
	if s, ok := val.(starlark.String); !ok || string(s) != "test" {
		t.Errorf("Expected 'test', got %v", val)
	}
}

func TestGoToStarlark_UnknownType(t *testing.T) {
	// Unknown types should be converted to string representation
	type customType struct {
		x int
	}
	input := customType{x: 42}
	result := goToStarlark(input)

	_, ok := result.(starlark.String)
	if !ok {
		t.Errorf("Expected string for unknown type, got %T", result)
	}
}

func TestCanWriteGlobalKey(t *testing.T) {
	tests := []struct {
		name            string
		allowedPatterns []string
		key             string
		expected        bool
	}{
		{
			"No patterns - deny all",
			[]string{},
			"any.key",
			false,
		},
		{
			"Exact pattern match",
			[]string{"presence.room"},
			"presence.room",
			true,
		},
		{
			"Exact pattern no match",
			[]string{"presence.room"},
			"presence.other",
			false,
		},
		{
			"Wildcard pattern match",
			[]string{"presence.*"},
			"presence.room",
			true,
		},
		{
			"Wildcard pattern no match",
			[]string{"presence.*"},
			"devices.light",
			false,
		},
		{
			"Multiple patterns - first matches",
			[]string{"presence.*", "devices.*"},
			"presence.room",
			true,
		},
		{
			"Multiple patterns - second matches",
			[]string{"presence.*", "devices.*"},
			"devices.light",
			true,
		},
		{
			"Multiple patterns - none match",
			[]string{"presence.*", "devices.*"},
			"sensors.temp",
			false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := &Context{
				allowedGlobalWrites: tt.allowedPatterns,
			}
			result := ctx.canWriteGlobalKey(tt.key)
			if result != tt.expected {
				t.Errorf("canWriteGlobalKey(%q) with patterns %v = %v, want %v",
					tt.key, tt.allowedPatterns, result, tt.expected)
			}
		})
	}
}

func TestRoundTrip_StarlarkGoStarlark(t *testing.T) {
	// Test that converting Starlark -> Go -> Starlark preserves values
	tests := []starlark.Value{
		starlark.None,
		starlark.True,
		starlark.False,
		starlark.MakeInt(42),
		starlark.Float(3.14),
		starlark.String("hello"),
	}

	for _, original := range tests {
		goVal := starlarkToGo(original)
		roundTripped := goToStarlark(goVal)

		// Compare string representations since direct comparison may not work
		if original.String() != roundTripped.String() {
			t.Errorf("Round trip failed: %v -> %v -> %v",
				original, goVal, roundTripped)
		}
	}
}
