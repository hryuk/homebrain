package mqtt

import (
	"sync"
	"time"
	"unicode/utf8"
)

// MessageEntry represents a captured MQTT message
type MessageEntry struct {
	Timestamp time.Time `json:"timestamp"`
	Topic     string    `json:"topic"`
	Payload   string    `json:"payload"`
	IsBinary  bool      `json:"is_binary"`
	Size      int       `json:"size"`
}

// MessageBuffer is a thread-safe circular buffer for MQTT messages
type MessageBuffer struct {
	messages []MessageEntry
	capacity int
	head     int
	count    int
	mu       sync.RWMutex
}

// NewMessageBuffer creates a new circular buffer with the specified capacity
func NewMessageBuffer(capacity int) *MessageBuffer {
	return &MessageBuffer{
		messages: make([]MessageEntry, capacity),
		capacity: capacity,
		head:     0,
		count:    0,
	}
}

// Add adds a new message to the buffer
func (b *MessageBuffer) Add(topic string, payload []byte) {
	b.mu.Lock()
	defer b.mu.Unlock()

	entry := MessageEntry{
		Timestamp: time.Now(),
		Topic:     topic,
		Size:      len(payload),
	}

	// Check if payload is valid UTF-8 text
	if utf8.Valid(payload) {
		entry.Payload = string(payload)
		entry.IsBinary = false
	} else {
		entry.Payload = ""
		entry.IsBinary = true
	}

	b.messages[b.head] = entry
	b.head = (b.head + 1) % b.capacity
	if b.count < b.capacity {
		b.count++
	}
}

// GetAll returns all messages in reverse chronological order (newest first)
func (b *MessageBuffer) GetAll() []MessageEntry {
	b.mu.RLock()
	defer b.mu.RUnlock()

	if b.count == 0 {
		return []MessageEntry{}
	}

	result := make([]MessageEntry, b.count)

	// Start from head-1 (most recent) and iterate backwards
	for i := 0; i < b.count; i++ {
		// head-1 is the most recent, head-2 is second most recent, etc.
		// Use modulo to wrap around the circular buffer
		idx := (b.head - 1 - i + b.capacity) % b.capacity
		result[i] = b.messages[idx]
	}

	return result
}

// GetRecent returns the most recent n messages (newest first)
func (b *MessageBuffer) GetRecent(n int) []MessageEntry {
	all := b.GetAll()
	if len(all) <= n {
		return all
	}
	// Already sorted newest-first, just take the first n
	return all[:n]
}

// Count returns the number of messages in the buffer
func (b *MessageBuffer) Count() int {
	b.mu.RLock()
	defer b.mu.RUnlock()
	return b.count
}

// Clear empties the buffer
func (b *MessageBuffer) Clear() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.head = 0
	b.count = 0
}
