#include "Arduino.h"
#include "esp_camera.h"
#include <WiFi.h>
#include "camera_pins.h"
#include "config.h"
#include "mqtt_client.h"

#define PIR_PIN    43
#define BUZZER_PIN 44
#define LED_PIN     3

#define VERIFY_WINDOW_MS  50
#define ALARM_DURATION_MS 1500
#define COOLDOWN_MS       5000

enum AlarmState { IDLE, VERIFYING, ALARMING, COOLDOWN };

volatile bool pirTriggered = false;
AlarmState state = IDLE;
unsigned long stateStartMs = 0;
bool armed = true;
bool manualAlarm = false;

void startCameraServer();
void setupLedFlash(int pin);
void captureLastAlertFrame();

void IRAM_ATTR handlePIR() {
  pirTriggered = true;
}

void setBuzzer(bool on) {
  digitalWrite(BUZZER_PIN, on ? HIGH : LOW);
  digitalWrite(LED_PIN, on ? HIGH : LOW);
}

void manualAlarmOn() {
  manualAlarm = true;
  pirTriggered = false;
  setBuzzer(true);
}

void manualAlarmOff() {
  manualAlarm = false;
  setBuzzer(false);
  state = IDLE;
  pirTriggered = false;
}

bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.frame_size = FRAMESIZE_UXGA;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  if (psramFound()) {
    config.jpeg_quality = 10;
    config.fb_count = 2;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    config.frame_size = FRAMESIZE_SVGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }

  if (esp_camera_init(&config) != ESP_OK) {
    return false;
  }

  sensor_t *s = esp_camera_sensor_get();
  if (s->id.PID == OV3660_PID) {
    s->set_vflip(s, 1);
    s->set_brightness(s, 1);
    s->set_saturation(s, -2);
  }
  s->set_framesize(s, FRAMESIZE_QVGA);
  s->set_gainceiling(s, GAINCEILING_128X);
  s->set_aec2(s, 1);
  s->set_ae_level(s, 1);

  setupLedFlash(LED_GPIO_NUM);
  return true;
}

void setup() {
  Serial.begin(115200);
  while (!Serial) {}
  Serial.println("Booting IRIS camera + PIR/buzzer...");

  if (!initCamera()) {
    Serial.println("Camera init failed. Halting.");
    while (true) { delay(1000); }
  }

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.setSleep(false);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Camera ready. Stream: http://");
  Serial.print(WiFi.localIP());
  Serial.println(":81/stream");

  startCameraServer();

  Serial.println("Connecting to MQTT broker...");
  mqttSetup();

  pinMode(PIR_PIN, INPUT_PULLDOWN);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(LED_PIN, LOW);

  Serial.println("System armed. Waiting for true motion...");
  Serial.flush();
  Serial.end();

  attachInterrupt(digitalPinToInterrupt(PIR_PIN), handlePIR, RISING);
}

void loop() {
  mqttLoop();

  if (manualAlarm) {
    return;
  }

  unsigned long now = millis();

  switch (state) {
    case IDLE:
      if (pirTriggered) {
        pirTriggered = false;
        detachInterrupt(digitalPinToInterrupt(PIR_PIN));
        state = VERIFYING;
        stateStartMs = now;
      }
      break;

    case VERIFYING:
      if (now - stateStartMs >= VERIFY_WINDOW_MS) {
        if (digitalRead(PIR_PIN) == HIGH && armed) {
          captureLastAlertFrame();
          setBuzzer(true);
          state = ALARMING;
          String imgUrl = "http://" + WiFi.localIP().toString() + "/capture/last.jpg";
          mqttPublishAlert("motion", "Motion Detected", imgUrl.c_str());
        } else {
          state = IDLE;
          attachInterrupt(digitalPinToInterrupt(PIR_PIN), handlePIR, RISING);
        }
        stateStartMs = now;
      }
      break;

    case ALARMING:
      if (now - stateStartMs >= ALARM_DURATION_MS) {
        setBuzzer(false);
        state = COOLDOWN;
        stateStartMs = now;
      }
      break;

    case COOLDOWN:
      if (now - stateStartMs >= COOLDOWN_MS) {
        pirTriggered = false;
        state = IDLE;
        attachInterrupt(digitalPinToInterrupt(PIR_PIN), handlePIR, RISING);
      }
      break;
  }
}
