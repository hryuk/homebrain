package runner

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"go.starlark.net/starlark"
)

// LibraryModule represents a loaded library module
type LibraryModule struct {
	Name        string
	FilePath    string
	Description string
	Functions   map[string]*starlark.Function
	Globals     starlark.StringDict
}

// LibraryManager manages library modules
type LibraryManager struct {
	modules map[string]*LibraryModule
	mu      sync.RWMutex
}

// NewLibraryManager creates a new library manager
func NewLibraryManager() *LibraryManager {
	return &LibraryManager{
		modules: make(map[string]*LibraryModule),
	}
}

// LoadLibraries loads all library modules from the lib/ directory
func (lm *LibraryManager) LoadLibraries(automationsPath string) error {
	libPath := filepath.Join(automationsPath, "lib")
	
	// Create lib directory if it doesn't exist
	if err := os.MkdirAll(libPath, 0755); err != nil {
		return fmt.Errorf("failed to create lib directory: %w", err)
	}

	// Find all .lib.star files
	files, err := filepath.Glob(filepath.Join(libPath, "*.lib.star"))
	if err != nil {
		return fmt.Errorf("failed to find library files: %w", err)
	}

	lm.mu.Lock()
	defer lm.mu.Unlock()

	// Clear existing modules
	lm.modules = make(map[string]*LibraryModule)

	// Load each library file
	for _, filePath := range files {
		if err := lm.loadLibrary(filePath); err != nil {
			return fmt.Errorf("failed to load library %s: %w", filePath, err)
		}
	}

	return nil
}

// loadLibrary loads a single library file
func (lm *LibraryManager) loadLibrary(filePath string) error {
	// Extract module name from filename (e.g., "timers.lib.star" -> "timers")
	filename := filepath.Base(filePath)
	name := strings.TrimSuffix(filename, ".lib.star")

	// Read the file
	data, err := os.ReadFile(filePath)
	if err != nil {
		return err
	}

	// Execute the Starlark file
	thread := &starlark.Thread{Name: "library:" + name}
	globals, err := starlark.ExecFile(thread, filePath, data, nil)
	if err != nil {
		return fmt.Errorf("starlark execution error: %w", err)
	}

	// Extract description from docstring if present
	description := ""
	if doc, ok := globals["__doc__"]; ok {
		if str, ok := doc.(starlark.String); ok {
			description = str.GoString()
		}
	}

	// Extract all functions
	functions := make(map[string]*starlark.Function)
	for name, value := range globals {
		if fn, ok := value.(*starlark.Function); ok {
			// Skip private functions (starting with _)
			if !strings.HasPrefix(name, "_") {
				functions[name] = fn
			}
		}
	}

	module := &LibraryModule{
		Name:        name,
		FilePath:    filePath,
		Description: description,
		Functions:   functions,
		Globals:     globals,
	}

	lm.modules[name] = module
	return nil
}

// GetModule returns a library module by name
func (lm *LibraryManager) GetModule(name string) (*LibraryModule, bool) {
	lm.mu.RLock()
	defer lm.mu.RUnlock()
	module, ok := lm.modules[name]
	return module, ok
}

// GetAllModules returns all loaded library modules
func (lm *LibraryManager) GetAllModules() map[string]*LibraryModule {
	lm.mu.RLock()
	defer lm.mu.RUnlock()
	
	// Return a copy to avoid concurrent modification issues
	result := make(map[string]*LibraryModule, len(lm.modules))
	for name, module := range lm.modules {
		result[name] = module
	}
	return result
}

// ToStarlarkStruct converts all library modules to a Starlark struct
// that can be accessed as ctx.lib.modulename.function()
func (lm *LibraryManager) ToStarlarkStruct() *starlark.Dict {
	lm.mu.RLock()
	defer lm.mu.RUnlock()

	libDict := starlark.NewDict(len(lm.modules))
	
	for moduleName, module := range lm.modules {
		// Create a struct for each module containing its functions
		moduleDict := starlark.StringDict{}
		for fnName, fn := range module.Functions {
			moduleDict[fnName] = fn
		}
		
		moduleStruct := starlark.NewDict(len(moduleDict))
		for k, v := range moduleDict {
			moduleStruct.SetKey(starlark.String(k), v)
		}
		
		libDict.SetKey(starlark.String(moduleName), moduleStruct)
	}
	
	return libDict
}

// GetFunctionInfo extracts function information for a library module
type FunctionInfo struct {
	Name      string
	DocString string
	Params    []string
}

// GetFunctionInfo returns information about functions in a module
func (lm *LibraryManager) GetFunctionInfo(moduleName string) ([]FunctionInfo, error) {
	module, ok := lm.GetModule(moduleName)
	if !ok {
		return nil, fmt.Errorf("module %s not found", moduleName)
	}

	var functions []FunctionInfo
	for name, fn := range module.Functions {
		info := FunctionInfo{
			Name:      name,
			DocString: extractDocstring(fn),
			Params:    extractParams(fn),
		}
		functions = append(functions, info)
	}

	return functions, nil
}

// extractDocstring extracts the docstring from a Starlark function
func extractDocstring(fn *starlark.Function) string {
	// Try to get the __doc__ attribute if it exists
	// Starlark functions don't have a built-in docstring accessor,
	// so we return empty string for now
	// The docstring can be parsed from the source code if needed
	return ""
}

// extractParams extracts parameter names from a Starlark function
func extractParams(fn *starlark.Function) []string {
	// Starlark doesn't expose parameter names easily at runtime
	// For now, return empty slice
	// This could be enhanced by parsing the source file
	return []string{}
}
