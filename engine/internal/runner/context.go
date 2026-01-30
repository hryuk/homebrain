package runner

import (
	"encoding/json"
	"fmt"
	"time"

	"go.starlark.net/starlark"
	"go.starlark.net/starlarkstruct"

	"github.com/homebrain/engine/internal/mqtt"
	"github.com/homebrain/engine/internal/state"
)

// Context provides the runtime context for Starlark automations
type Context struct {
	automationID        string
	mqttClient          *mqtt.Client
	stateStore          *state.Store
	logFunc             func(automationID, message string)
	allowedGlobalWrites []string // Patterns for allowed global state writes
	libraryManager      *LibraryManager
}

// NewContext creates a new automation context
func NewContext(automationID string, mqttClient *mqtt.Client, stateStore *state.Store, logFunc func(string, string), allowedGlobalWrites []string, libraryManager *LibraryManager) *Context {
	return &Context{
		automationID:        automationID,
		mqttClient:          mqttClient,
		stateStore:          stateStore,
		logFunc:             logFunc,
		allowedGlobalWrites: allowedGlobalWrites,
		libraryManager:      libraryManager,
	}
}

// ToStarlark converts the context to a Starlark struct
func (c *Context) ToStarlark() *starlarkstruct.Struct {
	dict := starlark.StringDict{
		"publish":      starlark.NewBuiltin("publish", c.publish),
		"log":          starlark.NewBuiltin("log", c.log),
		"json_encode":  starlark.NewBuiltin("json_encode", c.jsonEncode),
		"json_decode":  starlark.NewBuiltin("json_decode", c.jsonDecode),
		"get_state":    starlark.NewBuiltin("get_state", c.getState),
		"set_state":    starlark.NewBuiltin("set_state", c.setState),
		"clear_state":  starlark.NewBuiltin("clear_state", c.clearState),
		"get_global":   starlark.NewBuiltin("get_global", c.getGlobal),
		"set_global":   starlark.NewBuiltin("set_global", c.setGlobal),
		"clear_global": starlark.NewBuiltin("clear_global", c.clearGlobal),
		"now":          starlark.NewBuiltin("now", c.now),
	}
	
	// Add library modules if available
	if c.libraryManager != nil {
		dict["lib"] = c.libraryManager.ToStarlarkStruct()
	}
	
	return starlarkstruct.FromStringDict(starlarkstruct.Default, dict)
}

func (c *Context) publish(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var topic, payload string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "topic", &topic, "payload", &payload); err != nil {
		return nil, err
	}

	if err := c.mqttClient.Publish(topic, []byte(payload)); err != nil {
		return starlark.False, nil
	}
	return starlark.True, nil
}

func (c *Context) log(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var message string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "message", &message); err != nil {
		return nil, err
	}

	if c.logFunc != nil {
		c.logFunc(c.automationID, message)
	}
	return starlark.None, nil
}

func (c *Context) jsonEncode(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var val starlark.Value
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "value", &val); err != nil {
		return nil, err
	}

	goVal := starlarkToGo(val)
	data, err := json.Marshal(goVal)
	if err != nil {
		return nil, fmt.Errorf("json_encode: %w", err)
	}
	return starlark.String(data), nil
}

func (c *Context) jsonDecode(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var data string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "data", &data); err != nil {
		return nil, err
	}

	var goVal any
	if err := json.Unmarshal([]byte(data), &goVal); err != nil {
		return nil, fmt.Errorf("json_decode: %w", err)
	}
	return goToStarlark(goVal), nil
}

func (c *Context) getState(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key); err != nil {
		return nil, err
	}

	val, err := c.stateStore.GetState(c.automationID, key)
	if err != nil {
		return starlark.None, nil
	}
	if val == nil {
		return starlark.None, nil
	}
	return goToStarlark(val), nil
}

func (c *Context) setState(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	var val starlark.Value
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key, "value", &val); err != nil {
		return nil, err
	}

	goVal := starlarkToGo(val)
	if err := c.stateStore.SetState(c.automationID, key, goVal); err != nil {
		return starlark.False, nil
	}
	return starlark.True, nil
}

func (c *Context) clearState(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key); err != nil {
		return nil, err
	}

	if err := c.stateStore.ClearState(c.automationID, key); err != nil {
		return starlark.False, nil
	}
	return starlark.True, nil
}

func (c *Context) now(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	return starlark.Float(float64(time.Now().Unix())), nil
}

func (c *Context) getGlobal(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key); err != nil {
		return nil, err
	}

	val, err := c.stateStore.GetGlobalState(key)
	if err != nil {
		return starlark.None, nil
	}
	if val == nil {
		return starlark.None, nil
	}
	return goToStarlark(val), nil
}

func (c *Context) setGlobal(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	var val starlark.Value
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key, "value", &val); err != nil {
		return nil, err
	}

	// Check if this automation is allowed to write to this key
	if !c.canWriteGlobalKey(key) {
		c.logFunc(c.automationID, fmt.Sprintf("ERROR: Attempted to write to global key '%s' without permission. Add to global_state_writes in config.", key))
		return starlark.False, nil
	}

	goVal := starlarkToGo(val)
	if err := c.stateStore.SetGlobalState(key, goVal); err != nil {
		return starlark.False, nil
	}
	return starlark.True, nil
}

func (c *Context) clearGlobal(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
	var key string
	if err := starlark.UnpackArgs(fn.Name(), args, kwargs, "key", &key); err != nil {
		return nil, err
	}

	// Check if this automation is allowed to write to this key
	if !c.canWriteGlobalKey(key) {
		c.logFunc(c.automationID, fmt.Sprintf("ERROR: Attempted to clear global key '%s' without permission. Add to global_state_writes in config.", key))
		return starlark.False, nil
	}

	if err := c.stateStore.ClearGlobalState(key); err != nil {
		return starlark.False, nil
	}
	return starlark.True, nil
}

// canWriteGlobalKey checks if the automation is allowed to write to a global state key
func (c *Context) canWriteGlobalKey(key string) bool {
	for _, pattern := range c.allowedGlobalWrites {
		if matchPattern(pattern, key) {
			return true
		}
	}
	return false
}

// matchPattern checks if a key matches a pattern (supports * wildcard)
func matchPattern(pattern, key string) bool {
	// If pattern ends with *, match prefix
	if len(pattern) > 0 && pattern[len(pattern)-1] == '*' {
		prefix := pattern[:len(pattern)-1]
		return len(key) >= len(prefix) && key[:len(prefix)] == prefix
	}
	// Exact match
	return pattern == key
}

// starlarkToGo converts a Starlark value to a Go value
func starlarkToGo(val starlark.Value) any {
	switch v := val.(type) {
	case starlark.NoneType:
		return nil
	case starlark.Bool:
		return bool(v)
	case starlark.Int:
		i, _ := v.Int64()
		return i
	case starlark.Float:
		return float64(v)
	case starlark.String:
		return string(v)
	case *starlark.List:
		result := make([]any, v.Len())
		for i := 0; i < v.Len(); i++ {
			result[i] = starlarkToGo(v.Index(i))
		}
		return result
	case *starlark.Dict:
		result := make(map[string]any)
		for _, item := range v.Items() {
			key, ok := item[0].(starlark.String)
			if ok {
				result[string(key)] = starlarkToGo(item[1])
			}
		}
		return result
	default:
		return v.String()
	}
}

// goToStarlark converts a Go value to a Starlark value
func goToStarlark(val any) starlark.Value {
	switch v := val.(type) {
	case nil:
		return starlark.None
	case bool:
		return starlark.Bool(v)
	case int:
		return starlark.MakeInt(v)
	case int64:
		return starlark.MakeInt64(v)
	case float64:
		return starlark.Float(v)
	case string:
		return starlark.String(v)
	case []any:
		list := make([]starlark.Value, len(v))
		for i, item := range v {
			list[i] = goToStarlark(item)
		}
		return starlark.NewList(list)
	case map[string]any:
		dict := starlark.NewDict(len(v))
		for key, value := range v {
			dict.SetKey(starlark.String(key), goToStarlark(value))
		}
		return dict
	default:
		return starlark.String(fmt.Sprintf("%v", v))
	}
}
