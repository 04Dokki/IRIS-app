#ifndef IRIS_MQTT_CLIENT_H
#define IRIS_MQTT_CLIENT_H

#include <Arduino.h>

void mqttSetup();
void mqttLoop();
bool mqttIsConnected();
void mqttPublishAlert(const char *type, const char *label, const char *imgUrl);
void mqttPublishStatus();

#endif
