#include "mqtt_client.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <MQTT.h>
#include <ArduinoJson.h>
#include <time.h>
#include "config.h"

extern bool armed;
extern void manualAlarmOn();
extern void manualAlarmOff();

static WiFiClientSecure secureClient;
static MQTTClient mqttClient(1024);

static String topicStatus() { return String("iris/") + DEVICE_ID + "/status"; }
static String topicAlert()  { return String("iris/") + DEVICE_ID + "/alert"; }
static String topicCmd()    { return String("iris/") + DEVICE_ID + "/cmd"; }

static String buildStatusPayload(bool online) {
  JsonDocument doc;
  doc["online"] = online;
  doc["rssi"] = WiFi.RSSI();
  doc["uptime"] = millis() / 1000;
  doc["detect"] = armed;
  doc["stream"] = online ? ("http://" + WiFi.localIP().toString() + ":81/stream") : "";
  doc["faces"] = 0;
  String out;
  serializeJson(doc, out);
  return out;
}

static void mqttMessageReceived(String &topic, String &payload) {
  JsonDocument doc;
  if (deserializeJson(doc, payload) != DeserializationError::Ok) {
    return;
  }
  const char *command = doc["cmd"];
  if (!command) {
    return;
  }

  if (strcmp(command, "detect_on") == 0) {
    armed = true;
    mqttPublishStatus();
  } else if (strcmp(command, "detect_off") == 0) {
    armed = false;
    mqttPublishStatus();
  } else if (strcmp(command, "alarm_on") == 0) {
    manualAlarmOn();
  } else if (strcmp(command, "alarm_off") == 0) {
    manualAlarmOff();
  } else if (strcmp(command, "status") == 0) {
    mqttPublishStatus();
  } else if (strcmp(command, "reboot") == 0) {
    mqttClient.publish(topicStatus().c_str(), buildStatusPayload(false).c_str(), true, 1);
    mqttClient.disconnect();
    delay(200);
    ESP.restart();
  }
}

static bool mqttConnect() {
  String willTopic = topicStatus();
  String willPayload = buildStatusPayload(false);
  mqttClient.setWill(willTopic.c_str(), willPayload.c_str(), true, 1);

  bool ok = mqttClient.connect(DEVICE_ID, MQTT_USERNAME, MQTT_PASSWORD);

  if (ok) {
    mqttClient.subscribe(topicCmd().c_str(), 1);
    mqttPublishStatus();
  }
  return ok;
}

void mqttSetup() {
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  secureClient.setInsecure();
  mqttClient.begin(MQTT_HOST, MQTT_PORT, secureClient);
  mqttClient.onMessage(mqttMessageReceived);
  mqttClient.setKeepAlive(30);
  mqttConnect();
}

void mqttLoop() {
  static unsigned long lastReconnectAttempt = 0;
  static unsigned long lastStatusPublish = 0;
  unsigned long now = millis();

  mqttClient.loop();

  if (!mqttClient.connected()) {
    if (now - lastReconnectAttempt >= 5000) {
      lastReconnectAttempt = now;
      mqttConnect();
    }
    return;
  }

  if (now - lastStatusPublish >= 20000) {
    lastStatusPublish = now;
    mqttPublishStatus();
  }
}

bool mqttIsConnected() {
  return mqttClient.connected();
}

void mqttPublishStatus() {
  if (!mqttClient.connected()) {
    return;
  }
  mqttClient.publish(topicStatus().c_str(), buildStatusPayload(true).c_str(), true, 1);
}

void mqttPublishAlert(const char *type, const char *label, const char *imgUrl) {
  if (!mqttClient.connected()) {
    return;
  }
  time_t now;
  time(&now);

  JsonDocument doc;
  doc["type"] = type;
  doc["ts"] = (uint64_t)now * 1000ULL;
  doc["img"] = imgUrl;
  doc["label"] = label;

  String out;
  serializeJson(doc, out);
  mqttClient.publish(topicAlert().c_str(), out.c_str(), false, 1);
}
