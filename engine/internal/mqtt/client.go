package mqtt

import (
	"fmt"
	"log/slog"
	"sync"
	"time"

	paho "github.com/eclipse/paho.mqtt.golang"
)

type Config struct {
	Broker   string
	Username string
	Password string
	ClientID string
}

type MessageHandler func(topic string, payload []byte)

type Client struct {
	client           paho.Client
	handlers         map[string][]MessageHandler
	mu               sync.RWMutex
	discoveredTopics map[string]time.Time
	topicsMu         sync.RWMutex
}

func New(cfg Config) (*Client, error) {
	c := &Client{
		handlers:         make(map[string][]MessageHandler),
		discoveredTopics: make(map[string]time.Time),
	}

	opts := paho.NewClientOptions()
	opts.AddBroker(cfg.Broker)
	opts.SetClientID(cfg.ClientID)
	opts.SetAutoReconnect(true)
	opts.SetConnectRetry(true)
	opts.SetConnectRetryInterval(5 * time.Second)

	if cfg.Username != "" {
		opts.SetUsername(cfg.Username)
	}
	if cfg.Password != "" {
		opts.SetPassword(cfg.Password)
	}

	opts.SetOnConnectHandler(func(client paho.Client) {
		slog.Info("MQTT connected")
		// Resubscribe to all topics on reconnect
		c.mu.RLock()
		for topic := range c.handlers {
			c.subscribeInternal(topic)
		}
		c.mu.RUnlock()
	})

	opts.SetConnectionLostHandler(func(client paho.Client, err error) {
		slog.Warn("MQTT connection lost", "error", err)
	})

	c.client = paho.NewClient(opts)
	token := c.client.Connect()
	if token.Wait() && token.Error() != nil {
		return nil, fmt.Errorf("failed to connect to MQTT broker: %w", token.Error())
	}

	// Subscribe to wildcard to discover topics
	c.subscribeForDiscovery()

	return c, nil
}

func (c *Client) subscribeForDiscovery() {
	token := c.client.Subscribe("#", 0, func(client paho.Client, msg paho.Message) {
		c.topicsMu.Lock()
		c.discoveredTopics[msg.Topic()] = time.Now()
		c.topicsMu.Unlock()
	})
	token.Wait()
}

func (c *Client) GetDiscoveredTopics() []string {
	c.topicsMu.RLock()
	defer c.topicsMu.RUnlock()

	topics := make([]string, 0, len(c.discoveredTopics))
	for topic := range c.discoveredTopics {
		topics = append(topics, topic)
	}
	return topics
}

func (c *Client) Subscribe(topic string, handler MessageHandler) error {
	c.mu.Lock()
	c.handlers[topic] = append(c.handlers[topic], handler)
	c.mu.Unlock()

	return c.subscribeInternal(topic)
}

func (c *Client) subscribeInternal(topic string) error {
	token := c.client.Subscribe(topic, 1, func(client paho.Client, msg paho.Message) {
		c.mu.RLock()
		handlers := c.handlers[msg.Topic()]
		// Also check for wildcard subscriptions
		for pattern, h := range c.handlers {
			if matchTopic(pattern, msg.Topic()) && pattern != msg.Topic() {
				handlers = append(handlers, h...)
			}
		}
		c.mu.RUnlock()

		for _, handler := range handlers {
			go handler(msg.Topic(), msg.Payload())
		}
	})
	token.Wait()
	if token.Error() != nil {
		return fmt.Errorf("failed to subscribe to topic %s: %w", topic, token.Error())
	}
	slog.Debug("Subscribed to topic", "topic", topic)
	return nil
}

func (c *Client) Unsubscribe(topic string) error {
	c.mu.Lock()
	delete(c.handlers, topic)
	c.mu.Unlock()

	token := c.client.Unsubscribe(topic)
	token.Wait()
	if token.Error() != nil {
		return fmt.Errorf("failed to unsubscribe from topic %s: %w", topic, token.Error())
	}
	return nil
}

func (c *Client) Publish(topic string, payload []byte) error {
	token := c.client.Publish(topic, 1, false, payload)
	token.Wait()
	if token.Error() != nil {
		return fmt.Errorf("failed to publish to topic %s: %w", topic, token.Error())
	}
	slog.Debug("Published message", "topic", topic)
	return nil
}

func (c *Client) Disconnect() {
	c.client.Disconnect(1000)
}

// matchTopic checks if a topic matches a pattern with MQTT wildcards
func matchTopic(pattern, topic string) bool {
	if pattern == "#" {
		return true
	}
	if pattern == topic {
		return true
	}
	// Simple wildcard matching - could be extended for + wildcard
	return false
}
