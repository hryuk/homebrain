package runner

import (
	"os"
	"path/filepath"
	"testing"
)

func TestNewLibraryManager(t *testing.T) {
	lm := NewLibraryManager()
	if lm == nil {
		t.Fatal("Expected non-nil LibraryManager")
	}
	if lm.modules == nil {
		t.Fatal("Expected modules map to be initialized")
	}
}

func TestLibraryManager_LoadLibraries_EmptyDir(t *testing.T) {
	// Create temp directory
	tmpDir := t.TempDir()

	lm := NewLibraryManager()
	err := lm.LoadLibraries(tmpDir)
	if err != nil {
		t.Fatalf("Expected no error for empty directory, got: %v", err)
	}

	modules := lm.GetAllModules()
	if len(modules) != 0 {
		t.Errorf("Expected 0 modules, got %d", len(modules))
	}
}

func TestLibraryManager_LoadLibraries_SingleModule(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Create a test library file
	code := `
"""Test library module."""

def helper_function(ctx, value):
    """Doubles the value."""
    return value * 2

def another_helper(x):
    """Adds one."""
    return x + 1
`
	if err := os.WriteFile(filepath.Join(libDir, "testlib.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	err := lm.LoadLibraries(tmpDir)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	modules := lm.GetAllModules()
	if len(modules) != 1 {
		t.Errorf("Expected 1 module, got %d", len(modules))
	}

	module, ok := lm.GetModule("testlib")
	if !ok {
		t.Fatal("Expected to find 'testlib' module")
	}

	if module.Name != "testlib" {
		t.Errorf("Expected module name 'testlib', got '%s'", module.Name)
	}

	if len(module.Functions) != 2 {
		t.Errorf("Expected 2 functions, got %d", len(module.Functions))
	}

	if _, ok := module.Functions["helper_function"]; !ok {
		t.Error("Expected to find 'helper_function'")
	}

	if _, ok := module.Functions["another_helper"]; !ok {
		t.Error("Expected to find 'another_helper'")
	}
}

func TestLibraryManager_LoadLibraries_MultipleModules(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Create first library
	code1 := `
def func_a():
    return "a"
`
	if err := os.WriteFile(filepath.Join(libDir, "lib_a.lib.star"), []byte(code1), 0644); err != nil {
		t.Fatal(err)
	}

	// Create second library
	code2 := `
def func_b():
    return "b"
`
	if err := os.WriteFile(filepath.Join(libDir, "lib_b.lib.star"), []byte(code2), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	err := lm.LoadLibraries(tmpDir)
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	modules := lm.GetAllModules()
	if len(modules) != 2 {
		t.Errorf("Expected 2 modules, got %d", len(modules))
	}

	if _, ok := lm.GetModule("lib_a"); !ok {
		t.Error("Expected to find 'lib_a' module")
	}

	if _, ok := lm.GetModule("lib_b"); !ok {
		t.Error("Expected to find 'lib_b' module")
	}
}

func TestLibraryManager_LoadLibraries_PrivateFunctionsExcluded(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	code := `
def public_func():
    return "public"

def _private_func():
    return "private"

def __very_private():
    return "very private"
`
	if err := os.WriteFile(filepath.Join(libDir, "test.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	module, _ := lm.GetModule("test")
	if len(module.Functions) != 1 {
		t.Errorf("Expected 1 public function, got %d", len(module.Functions))
	}

	if _, ok := module.Functions["public_func"]; !ok {
		t.Error("Expected to find 'public_func'")
	}

	if _, ok := module.Functions["_private_func"]; ok {
		t.Error("Private function should be excluded")
	}
}

func TestLibraryManager_LoadLibraries_SyntaxError(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	code := `
def broken_function(  # Missing closing paren and colon
    return "oops"
`
	if err := os.WriteFile(filepath.Join(libDir, "broken.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	err := lm.LoadLibraries(tmpDir)
	if err == nil {
		t.Fatal("Expected error for syntax error in library")
	}
}

func TestLibraryManager_LoadLibraries_ClearsExisting(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Create initial library
	code := `
def func_one():
    return 1
`
	if err := os.WriteFile(filepath.Join(libDir, "lib_one.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	if len(lm.GetAllModules()) != 1 {
		t.Fatal("Expected 1 module after first load")
	}

	// Delete the file and create a different one
	os.Remove(filepath.Join(libDir, "lib_one.lib.star"))
	code2 := `
def func_two():
    return 2
`
	if err := os.WriteFile(filepath.Join(libDir, "lib_two.lib.star"), []byte(code2), 0644); err != nil {
		t.Fatal(err)
	}

	// Reload libraries
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	modules := lm.GetAllModules()
	if len(modules) != 1 {
		t.Errorf("Expected 1 module after reload, got %d", len(modules))
	}

	if _, ok := lm.GetModule("lib_one"); ok {
		t.Error("Old module 'lib_one' should have been removed")
	}

	if _, ok := lm.GetModule("lib_two"); !ok {
		t.Error("New module 'lib_two' should exist")
	}
}

func TestLibraryManager_GetModule_NotFound(t *testing.T) {
	lm := NewLibraryManager()
	_, ok := lm.GetModule("nonexistent")
	if ok {
		t.Error("Expected GetModule to return false for nonexistent module")
	}
}

func TestLibraryManager_ToStarlarkStruct(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	code := `
def helper():
    return "hello"
`
	if err := os.WriteFile(filepath.Join(libDir, "utils.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	starlarkDict := lm.ToStarlarkStruct()
	if starlarkDict == nil {
		t.Fatal("Expected non-nil Starlark dict")
	}

	if starlarkDict.Len() != 1 {
		t.Errorf("Expected 1 module in dict, got %d", starlarkDict.Len())
	}
}

func TestLibraryManager_GetFunctionInfo(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	code := `
def func_a():
    return "a"

def func_b(x):
    return x
`
	if err := os.WriteFile(filepath.Join(libDir, "test.lib.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	funcs, err := lm.GetFunctionInfo("test")
	if err != nil {
		t.Fatalf("Expected no error, got: %v", err)
	}

	if len(funcs) != 2 {
		t.Errorf("Expected 2 functions, got %d", len(funcs))
	}
}

func TestLibraryManager_GetFunctionInfo_ModuleNotFound(t *testing.T) {
	lm := NewLibraryManager()
	_, err := lm.GetFunctionInfo("nonexistent")
	if err == nil {
		t.Error("Expected error for nonexistent module")
	}
}

func TestLibraryManager_IgnoresNonLibraryFiles(t *testing.T) {
	tmpDir := t.TempDir()
	libDir := filepath.Join(tmpDir, "lib")
	if err := os.MkdirAll(libDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Create a regular .star file (should be ignored in lib/)
	code := `
def func():
    return 1
`
	if err := os.WriteFile(filepath.Join(libDir, "regular.star"), []byte(code), 0644); err != nil {
		t.Fatal(err)
	}

	// Create a .txt file (should be ignored)
	if err := os.WriteFile(filepath.Join(libDir, "readme.txt"), []byte("readme"), 0644); err != nil {
		t.Fatal(err)
	}

	// Create a proper library file
	libCode := `
def lib_func():
    return 2
`
	if err := os.WriteFile(filepath.Join(libDir, "proper.lib.star"), []byte(libCode), 0644); err != nil {
		t.Fatal(err)
	}

	lm := NewLibraryManager()
	if err := lm.LoadLibraries(tmpDir); err != nil {
		t.Fatal(err)
	}

	modules := lm.GetAllModules()
	if len(modules) != 1 {
		t.Errorf("Expected 1 module (only .lib.star files), got %d", len(modules))
	}

	if _, ok := lm.GetModule("proper"); !ok {
		t.Error("Expected to find 'proper' module")
	}

	if _, ok := lm.GetModule("regular"); ok {
		t.Error("Regular .star file should not be loaded as library")
	}
}
